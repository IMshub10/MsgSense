package com.summer.notifai.banking_test_onxx

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
 * Stage 3 JVM unit test: ONNX argmax on the host using the desktop
 * `onnxruntime` (not `onnxruntime-android`).
 *
 * Validates that the `model.onnx` + `model.onnx.data` pair bundled in
 * `banking_test_onxx/` produces argmax identical to the Python pipeline's
 * golden output.
 */
class BankingOnnxArgmaxTest {

    data class GoldenTestCase(
        val sender: String,
        val body: String,
        val input_ids: List<Int>,
        val attention_mask: List<Int>,
        val argmax: List<Int>,
    )

    companion object {
        private const val ASSET_DIR = "src/main/assets/banking_test_onxx"

        private lateinit var ortEnv: OrtEnvironment
        private lateinit var ortSession: OrtSession
        private lateinit var tempDir: File

        @JvmStatic
        @BeforeClass
        fun setup() {
            ortEnv = OrtEnvironment.getEnvironment()

            // ONNX external data requires both files in the same directory,
            // loaded by file path (not byte buffer).
            tempDir = File(System.getProperty("java.io.tmpdir"), "banking_onnx_test")
            tempDir.mkdirs()

            val modelSrc = File(ASSET_DIR, "model.onnx")
            val dataSrc = File(ASSET_DIR, "model.onnx.data")
            require(modelSrc.exists()) { "model.onnx not found at ${modelSrc.absolutePath}" }
            require(dataSrc.exists()) { "model.onnx.data not found at ${dataSrc.absolutePath}" }

            val modelDst = File(tempDir, "model.onnx")
            val dataDst = File(tempDir, "model.onnx.data")
            modelSrc.copyTo(modelDst, overwrite = true)
            dataSrc.copyTo(dataDst, overwrite = true)

            val opts = OrtSession.SessionOptions()
            ortSession = ortEnv.createSession(modelDst.absolutePath, opts)
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
        require(goldenFile.exists()) { "golden_test.json not found at ${goldenFile.absolutePath}" }
        val type = object : TypeToken<List<GoldenTestCase>>() {}.type
        return Gson().fromJson(goldenFile.readText(), type)
    }

    @Test
    fun stage3_onnxArgmax_allCases() {
        val cases = loadGoldenTests()
        var passed = 0
        val failures = mutableListOf<String>()

        for ((index, tc) in cases.withIndex()) {
            val inputIds = LongArray(128) { tc.input_ids[it].toLong() }
            val mask = LongArray(128) { tc.attention_mask[it].toLong() }

            val inputTensor = OnnxTensor.createTensor(
                ortEnv, LongBuffer.wrap(inputIds), longArrayOf(1, 128)
            )
            val maskTensor = OnnxTensor.createTensor(
                ortEnv, LongBuffer.wrap(mask), longArrayOf(1, 128)
            )

            val feeds = mapOf(
                "input_ids" to inputTensor,
                "attention_mask" to maskTensor,
            )

            val result = ortSession.run(feeds)
            @Suppress("UNCHECKED_CAST")
            val logits = (result[0].value as Array<Array<FloatArray>>)[0]

            val actualArgmax = logits.map { row ->
                row.indices.maxByOrNull { row[it] }!!
            }

            inputTensor.close()
            maskTensor.close()
            result.close()

            if (actualArgmax != tc.argmax) {
                val firstDiff = actualArgmax.zip(tc.argmax).indexOfFirst { (a, e) -> a != e }
                val diff = buildString {
                    appendLine("FAIL Case $index (sender=${tc.sender}):")
                    appendLine("  first diff at position $firstDiff")
                    appendLine("  expected=${tc.argmax.getOrNull(firstDiff)}, actual=${actualArgmax.getOrNull(firstDiff)}")
                    val diffs = actualArgmax.zip(tc.argmax)
                        .withIndex()
                        .filter { (_, pair) -> pair.first != pair.second }
                    appendLine("  total mismatched positions: ${diffs.size}")
                    diffs.take(10).forEach { (i, pair) ->
                        appendLine("    [$i] expected=${pair.second} actual=${pair.first}")
                    }
                }
                failures.add(diff)
                System.err.println(diff)
            } else {
                passed++
            }
        }

        println("Stage 3 ONNX argmax: $passed/${cases.size} PASSED, ${failures.size} FAILED")
        if (failures.isNotEmpty()) {
            fail("${failures.size} case(s) failed. First failure:\n${failures.first()}")
        }
    }
}
