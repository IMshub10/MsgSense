package com.summer.notifai.banking_test

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.AfterClass
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.nio.LongBuffer

/**
 * End-to-end pipeline test: Stage 1 → 2 → 3 → 4, each stage consumes the
 * previous stage's actual output (not golden data). Validates final entities
 * and intermediate results against golden_test.json.
 */
class BankingEndToEndTest {

    data class GoldenTestCase(
        val sender: String,
        val body: String,
        val words: List<String>,
        val input_ids: List<Int>,
        val attention_mask: List<Int>,
        val word_ids: List<Int>,
        val argmax: List<Int>,
        val word_labels: List<String>,
        val entities: Map<String, List<String>>,
    )

    companion object {
        private const val ASSET_DIR = "src/main/assets/banking_test"
        private const val RUST_LIB_DIR =
            "src/main/java/com/summer/notifai/banking_test/rust_tokenizer/target/release"
        private const val TOKENIZER_PATH = "$ASSET_DIR/tokenizer.json"

        private lateinit var ortEnv: OrtEnvironment
        private lateinit var ortSession: OrtSession
        private lateinit var tempDir: File

        @JvmStatic
        @BeforeClass
        fun setup() {
            // Rust tokenizer
            val libDir = File(RUST_LIB_DIR)
            val dylibFile = File(libDir, "libhf_tokenizer_jni.dylib")
            val soFile = File(libDir, "libhf_tokenizer_jni.so")
            val libFile = when {
                dylibFile.exists() -> dylibFile
                soFile.exists() -> soFile
                else -> error("Native lib not found in $libDir")
            }
            HfTokenizerBridge.loadLibraryFromPath(libFile.absolutePath)
            HfTokenizerBridge.loadTokenizer(File(TOKENIZER_PATH).absolutePath)

            // ONNX model
            ortEnv = OrtEnvironment.getEnvironment()
            tempDir = File(System.getProperty("java.io.tmpdir"), "banking_e2e_test")
            tempDir.mkdirs()
            File(ASSET_DIR, "model.onnx").copyTo(File(tempDir, "model.onnx"), overwrite = true)
            File(ASSET_DIR, "model.onnx.data").copyTo(File(tempDir, "model.onnx.data"), overwrite = true)
            ortSession = ortEnv.createSession(
                File(tempDir, "model.onnx").absolutePath, OrtSession.SessionOptions()
            )
        }

        @JvmStatic
        @AfterClass
        fun teardown() {
            ortSession.close()
            ortEnv.close()
            tempDir.deleteRecursively()
        }
    }

    private fun loadGoldenTests(): List<GoldenTestCase> {
        val goldenFile = File("$ASSET_DIR/golden_test.json")
        require(goldenFile.exists()) { "golden_test.json not found" }
        val type = object : TypeToken<List<GoldenTestCase>>() {}.type
        return Gson().fromJson(goldenFile.readText(), type)
    }

    private fun runOnnxArgmax(inputIds: IntArray, attentionMask: IntArray): List<Int> {
        val ids = LongArray(128) { inputIds[it].toLong() }
        val mask = LongArray(128) { attentionMask[it].toLong() }

        val idTensor = OnnxTensor.createTensor(ortEnv, LongBuffer.wrap(ids), longArrayOf(1, 128))
        val maskTensor = OnnxTensor.createTensor(ortEnv, LongBuffer.wrap(mask), longArrayOf(1, 128))

        val result = ortSession.run(mapOf("input_ids" to idTensor, "attention_mask" to maskTensor))
        @Suppress("UNCHECKED_CAST")
        val logits = (result[0].value as Array<Array<FloatArray>>)[0]
        val argmax = logits.map { row -> row.indices.maxByOrNull { row[it] }!! }

        idTensor.close()
        maskTensor.close()
        result.close()
        return argmax
    }

    @Test
    fun endToEnd_allCases() {
        val cases = loadGoldenTests()
        var passed = 0
        val failures = mutableListOf<String>()

        for ((index, tc) in cases.withIndex()) {
            val mismatches = mutableListOf<String>()

            // Stage 1: pre-tokenize
            val words = BankingPreTokenizer.tokenize(tc.sender, tc.body)
            if (words != tc.words) {
                mismatches.add("  Stage 1 words: expected size=${tc.words.size}, actual size=${words.size}")
            }

            // Stage 2: tokenize (using Stage 1 output)
            val tokResult = HfTokenizerBridge.encode(words)
            val inputIds = tokResult.inputIds
            val attentionMask = tokResult.attentionMask
            val wordIds = tokResult.wordIds

            if (inputIds.toList() != tc.input_ids) {
                val first = inputIds.toList().zip(tc.input_ids).indexOfFirst { (a, e) -> a != e }
                mismatches.add("  Stage 2 input_ids: first diff at [$first]")
            }
            if (attentionMask.toList() != tc.attention_mask) {
                mismatches.add("  Stage 2 attention_mask: mismatch")
            }
            if (wordIds.toList() != tc.word_ids) {
                val first = wordIds.toList().zip(tc.word_ids).indexOfFirst { (a, e) -> a != e }
                mismatches.add("  Stage 2 word_ids: first diff at [$first]")
            }

            // Stage 3: ONNX inference (using Stage 2 output)
            val argmax = runOnnxArgmax(inputIds, attentionMask)
            if (argmax != tc.argmax) {
                val first = argmax.zip(tc.argmax).indexOfFirst { (a, e) -> a != e }
                mismatches.add("  Stage 3 argmax: first diff at [$first]")
            }

            // Stage 4: BIO decode (using Stage 3 + Stage 2 output)
            val wordLabels = BankingBioDecoder.collapseToWordLabels(argmax, wordIds.toList())
            val entities = BankingBioDecoder.groupEntities(words, wordLabels)

            if (wordLabels != tc.word_labels) {
                mismatches.add("  Stage 4 word_labels: expected=${tc.word_labels} actual=$wordLabels")
            }
            if (entities != tc.entities) {
                val allKeys = (entities.keys + tc.entities.keys).distinct()
                for (key in allKeys) {
                    if (entities[key] != tc.entities[key]) {
                        mismatches.add("  Stage 4 entities[$key]: expected=${tc.entities[key]} actual=${entities[key]}")
                    }
                }
            }

            if (mismatches.isNotEmpty()) {
                val diff = buildString {
                    appendLine("FAIL Case $index (sender=${tc.sender}):")
                    appendLine("  body = ${tc.body}")
                    mismatches.forEach { appendLine(it) }
                }
                failures.add(diff)
                System.err.println(diff)
            } else {
                passed++
            }
        }

        println("End-to-end pipeline: $passed/${cases.size} PASSED, ${failures.size} FAILED")
        if (failures.isNotEmpty()) {
            fail("${failures.size} case(s) failed. First failure:\n${failures.first()}")
        }
    }
}
