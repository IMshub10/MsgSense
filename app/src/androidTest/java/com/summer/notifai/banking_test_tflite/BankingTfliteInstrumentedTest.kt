package com.summer.notifai.banking_test_tflite

import android.content.res.AssetFileDescriptor
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.summer.notifai.banking_ner.BankingBioDecoder
import com.summer.notifai.banking_ner.BankingPreTokenizer
import com.summer.notifai.banking_ner.HfTokenizerBridge
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Full 4-stage TFLite banking NER pipeline validated on-device (ART) using
 * `org.tensorflow:tensorflow-lite` and the `banking_test_tflite/` assets.
 *
 * TFLite golden data is produced by the TFLite-Python reference pipeline,
 * so this test asserts Android-TFLite matches TFLite-Python exactly.
 *
 * Differences vs. the ONNX androidTest:
 * - Model weights are embedded inside `model.tflite`; we can load from a
 *   `MappedByteBuffer` and skip the "copy to cacheDir" step.
 * - Inputs are `int64` tensors shaped `[1, 128]`.
 * - Output is a `[1, 128, 25]` float tensor (logits over 25 BIO labels).
 */
@RunWith(AndroidJUnit4::class)
class BankingTfliteInstrumentedTest {

    private data class GoldenTestCase(
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
        private const val TAG = "BankingTflite"
        private const val SEQ_LEN = 128
        private const val NUM_LABELS = 25
    }

    private lateinit var cases: List<GoldenTestCase>
    private lateinit var interpreter: Interpreter
    private var inputIdsIdx: Int = -1
    private var attnMaskIdx: Int = -1

    private fun loadModelFile(assetPath: String): MappedByteBuffer {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val afd: AssetFileDescriptor = ctx.assets.openFd(assetPath)
        FileInputStream(afd.fileDescriptor).use { fis ->
            return fis.channel.map(
                FileChannel.MapMode.READ_ONLY,
                afd.startOffset,
                afd.declaredLength,
            )
        }
    }

    @Before
    fun setup() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext

        val goldenJson = ctx.assets.open(BankingTfliteAssets.goldenTestPath())
            .bufferedReader().use { it.readText() }
        val type = object : TypeToken<List<GoldenTestCase>>() {}.type
        cases = Gson().fromJson(goldenJson, type)
        Log.d(TAG, "Loaded ${cases.size} TFLite golden test cases")

        // Stage 2: shared Rust tokenizer. tokenizer.json shipped under TFLite
        // assets (same albert-base-v2 file as ONNX, just colocated for clarity).
        val tokenizerFile = File(ctx.cacheDir, "tflite_tokenizer.json")
        ctx.assets.open(BankingTfliteAssets.tokenizerPath()).use { input ->
            tokenizerFile.outputStream().use { output -> input.copyTo(output) }
        }
        HfTokenizerBridge.loadLibrary()
        HfTokenizerBridge.loadTokenizer(tokenizerFile.absolutePath)
        Log.d(TAG, "Rust HF tokenizer loaded")

        // Stage 3: TFLite model — loaded directly from assets via mmap'd
        // ByteBuffer (weights are embedded inside model.tflite, no side file).
        val modelBuffer = loadModelFile(BankingTfliteAssets.modelPath())
        interpreter = Interpreter(modelBuffer, Interpreter.Options())

        val inputCount = interpreter.inputTensorCount
        for (i in 0 until inputCount) {
            val t = interpreter.getInputTensor(i)
            Log.d(TAG, "TFLite input $i: name=${t.name()} shape=${t.shape().toList()} dtype=${t.dataType()}")
            val name = t.name()
            when {
                name.contains("input_ids", ignoreCase = true) -> inputIdsIdx = i
                name.contains("attention_mask", ignoreCase = true) -> attnMaskIdx = i
            }
        }
        require(inputIdsIdx >= 0 && attnMaskIdx >= 0) {
            "Couldn't locate input_ids / attention_mask tensors in TFLite model"
        }
        Log.d(TAG, "TFLite model loaded (input_ids=$inputIdsIdx, attention_mask=$attnMaskIdx)")
    }

    @After
    fun tearDown() {
        interpreter.close()
    }

    /** Runs TFLite on prepared int arrays and returns length-128 argmax over 25 classes. */
    private fun runTfliteArgmax(inputIds: IntArray, attentionMask: IntArray): List<Int> {
        val ids: Array<LongArray> = arrayOf(LongArray(SEQ_LEN) { inputIds[it].toLong() })
        val mask: Array<LongArray> = arrayOf(LongArray(SEQ_LEN) { attentionMask[it].toLong() })

        val inputs = arrayOfNulls<Any>(interpreter.inputTensorCount)
        inputs[inputIdsIdx] = ids
        inputs[attnMaskIdx] = mask

        val output: Array<Array<FloatArray>> =
            Array(1) { Array(SEQ_LEN) { FloatArray(NUM_LABELS) } }
        val outputs = mapOf(0 to output as Any)

        interpreter.runForMultipleInputsOutputs(inputs, outputs)

        val logits = output[0]
        return logits.map { row -> row.indices.maxByOrNull { row[it] }!! }
    }

    @Test
    fun stage1_preTokenizer_allCases() {
        var passed = 0
        val failures = mutableListOf<String>()
        for ((i, tc) in cases.withIndex()) {
            val actual = BankingPreTokenizer.tokenize(tc.sender, tc.body)
            if (actual == tc.words) {
                passed++
                Log.d(TAG, "S1 Case $i PASS")
            } else {
                val msg = "S1 Case $i FAIL (sender=${tc.sender})\n" +
                    "  expected(${tc.words.size}): ${tc.words}\n" +
                    "  actual  (${actual.size}): $actual"
                Log.e(TAG, msg)
                failures.add(msg)
            }
        }
        Log.d(TAG, "Stage 1: $passed/${cases.size} passed")
        assertEquals("Stage 1 failures:\n${failures.joinToString("\n")}", 0, failures.size)
    }

    @Test
    fun stage2_tokenizer_allCases() {
        var passed = 0
        val failures = mutableListOf<String>()
        for ((i, tc) in cases.withIndex()) {
            val tokResult = HfTokenizerBridge.encode(tc.words)
            val idsOk = tokResult.inputIds.toList() == tc.input_ids
            val maskOk = tokResult.attentionMask.toList() == tc.attention_mask
            val widsOk = tokResult.wordIds.toList() == tc.word_ids
            if (idsOk && maskOk && widsOk) {
                passed++
                Log.d(TAG, "S2 Case $i PASS")
            } else {
                val msg = "S2 Case $i FAIL (sender=${tc.sender}) ids=$idsOk mask=$maskOk wids=$widsOk"
                Log.e(TAG, msg)
                failures.add(msg)
            }
        }
        Log.d(TAG, "Stage 2: $passed/${cases.size} passed")
        assertEquals("Stage 2 failures:\n${failures.joinToString("\n")}", 0, failures.size)
    }

    @Test
    fun stage3_tfliteArgmax_allCases() {
        var passed = 0
        val failures = mutableListOf<String>()
        for ((i, tc) in cases.withIndex()) {
            val argmax = runTfliteArgmax(tc.input_ids.toIntArray(), tc.attention_mask.toIntArray())
            if (argmax == tc.argmax) {
                passed++
                Log.d(TAG, "S3 Case $i PASS")
            } else {
                val firstDiff = argmax.zip(tc.argmax).indexOfFirst { (a, e) -> a != e }
                val msg = "S3 Case $i FAIL (sender=${tc.sender}) firstDiff=$firstDiff " +
                    "expected=${tc.argmax.getOrNull(firstDiff)} actual=${argmax.getOrNull(firstDiff)}"
                Log.e(TAG, msg)
                failures.add(msg)
            }
        }
        Log.d(TAG, "Stage 3: $passed/${cases.size} passed")
        assertEquals("Stage 3 failures:\n${failures.joinToString("\n")}", 0, failures.size)
    }

    @Test
    fun stage4_bioDecoder_allCases() {
        var passed = 0
        val failures = mutableListOf<String>()
        for ((i, tc) in cases.withIndex()) {
            val wordLabels = BankingBioDecoder.collapseToWordLabels(tc.argmax, tc.word_ids)
            val entities = BankingBioDecoder.groupEntities(tc.words, wordLabels)
            val labelsOk = wordLabels == tc.word_labels
            val entitiesOk = entities == tc.entities
            if (labelsOk && entitiesOk) {
                passed++
                Log.d(TAG, "S4 Case $i PASS")
            } else {
                val msg = "S4 Case $i FAIL (sender=${tc.sender}) labels=$labelsOk entities=$entitiesOk"
                Log.e(TAG, msg)
                failures.add(msg)
            }
        }
        Log.d(TAG, "Stage 4: $passed/${cases.size} passed")
        assertEquals("Stage 4 failures:\n${failures.joinToString("\n")}", 0, failures.size)
    }

    @Test
    fun endToEnd_fullPipeline_allCases() {
        var passed = 0
        val failures = mutableListOf<String>()
        val startTime = System.currentTimeMillis()

        for ((i, tc) in cases.withIndex()) {
            val words = BankingPreTokenizer.tokenize(tc.sender, tc.body)
            val s1Ok = words == tc.words

            val tokResult = HfTokenizerBridge.encode(words)
            val s2Ok = tokResult.inputIds.toList() == tc.input_ids
                && tokResult.attentionMask.toList() == tc.attention_mask
                && tokResult.wordIds.toList() == tc.word_ids

            val argmax = runTfliteArgmax(tokResult.inputIds, tokResult.attentionMask)
            val s3Ok = argmax == tc.argmax

            val wordLabels = BankingBioDecoder.collapseToWordLabels(argmax, tokResult.wordIds.toList())
            val entities = BankingBioDecoder.groupEntities(words, wordLabels)
            val s4Ok = wordLabels == tc.word_labels && entities == tc.entities

            val allOk = s1Ok && s2Ok && s3Ok && s4Ok
            if (allOk) {
                passed++
                Log.d(TAG, "E2E Case $i PASS")
            } else {
                val ok = { b: Boolean -> if (b) "OK" else "FAIL" }
                val msg = "E2E Case $i FAIL (sender=${tc.sender}) S1=${ok(s1Ok)} S2=${ok(s2Ok)} S3=${ok(s3Ok)} S4=${ok(s4Ok)}"
                Log.e(TAG, msg)
                failures.add(msg)
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        Log.d(TAG, "End-to-end: $passed/${cases.size} passed in ${elapsed}ms")
        assertEquals("E2E failures:\n${failures.joinToString("\n")}", 0, failures.size)
    }
}
