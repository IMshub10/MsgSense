package com.summer.notifai.banking_test_onxx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
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
import java.io.File
import java.nio.LongBuffer

/**
 * Full 4-stage ONNX banking NER pipeline validated on-device (ART) using
 * `onnxruntime-android` and the `banking_test_onxx/` assets.
 */
@RunWith(AndroidJUnit4::class)
class BankingOnnxInstrumentedTest {

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
        private const val TAG = "BankingOnnx"
    }

    private lateinit var cases: List<GoldenTestCase>
    private lateinit var ortEnv: OrtEnvironment
    private lateinit var ortSession: OrtSession

    @Before
    fun setup() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext

        val goldenJson = ctx.assets.open(BankingOnnxAssets.goldenTestPath())
            .bufferedReader().use { it.readText() }
        val type = object : TypeToken<List<GoldenTestCase>>() {}.type
        cases = Gson().fromJson(goldenJson, type)
        Log.d(TAG, "Loaded ${cases.size} golden test cases")

        // Stage 2: Rust tokenizer (shared). `tokenizer.json` lives with ONNX assets
        // because both ONNX + TFLite use the same albert-base-v2 tokenizer.
        val tokenizerFile = File(ctx.cacheDir, "onnx_tokenizer.json")
        ctx.assets.open(BankingOnnxAssets.tokenizerPath()).use { input ->
            tokenizerFile.outputStream().use { output -> input.copyTo(output) }
        }
        HfTokenizerBridge.loadLibrary()
        HfTokenizerBridge.loadTokenizer(tokenizerFile.absolutePath)
        Log.d(TAG, "Rust HF tokenizer loaded")

        // Stage 3: ONNX model (external data requires a filesystem path, so we
        // copy model.onnx + model.onnx.data out of assets into cacheDir together).
        val modelDir = File(ctx.cacheDir, "banking_onnx")
        modelDir.mkdirs()
        for (name in listOf(BankingOnnxAssets.MODEL_FILE, BankingOnnxAssets.MODEL_DATA_FILE)) {
            ctx.assets.open("${BankingOnnxAssets.ASSET_SUBDIR}/$name").use { input ->
                File(modelDir, name).outputStream().use { output -> input.copyTo(output) }
            }
        }
        ortEnv = OrtEnvironment.getEnvironment()
        ortSession = ortEnv.createSession(
            File(modelDir, BankingOnnxAssets.MODEL_FILE).absolutePath,
            OrtSession.SessionOptions()
        )
        Log.d(TAG, "ONNX model loaded")
    }

    @After
    fun tearDown() {
        ortSession.close()
        ortEnv.close()
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
    fun stage3_onnxArgmax_allCases() {
        var passed = 0
        val failures = mutableListOf<String>()

        for ((i, tc) in cases.withIndex()) {
            val ids = LongArray(128) { tc.input_ids[it].toLong() }
            val mask = LongArray(128) { tc.attention_mask[it].toLong() }
            val idTensor = OnnxTensor.createTensor(ortEnv, LongBuffer.wrap(ids), longArrayOf(1, 128))
            val maskTensor = OnnxTensor.createTensor(ortEnv, LongBuffer.wrap(mask), longArrayOf(1, 128))
            val result = ortSession.run(mapOf("input_ids" to idTensor, "attention_mask" to maskTensor))

            @Suppress("UNCHECKED_CAST")
            val logits = (result[0].value as Array<Array<FloatArray>>)[0]
            val argmax = logits.map { row -> row.indices.maxByOrNull { row[it] }!! }
            idTensor.close(); maskTensor.close(); result.close()

            if (argmax == tc.argmax) {
                passed++
                Log.d(TAG, "S3 Case $i PASS")
            } else {
                val msg = "S3 Case $i FAIL (sender=${tc.sender})"
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
            val inputIds = tokResult.inputIds
            val attentionMask = tokResult.attentionMask
            val wordIds = tokResult.wordIds
            val s2Ok = inputIds.toList() == tc.input_ids
                && attentionMask.toList() == tc.attention_mask
                && wordIds.toList() == tc.word_ids

            val ids = LongArray(128) { inputIds[it].toLong() }
            val mask = LongArray(128) { attentionMask[it].toLong() }
            val idTensor = OnnxTensor.createTensor(ortEnv, LongBuffer.wrap(ids), longArrayOf(1, 128))
            val maskTensor = OnnxTensor.createTensor(ortEnv, LongBuffer.wrap(mask), longArrayOf(1, 128))
            val result = ortSession.run(mapOf("input_ids" to idTensor, "attention_mask" to maskTensor))
            @Suppress("UNCHECKED_CAST")
            val logits = (result[0].value as Array<Array<FloatArray>>)[0]
            val argmax = logits.map { row -> row.indices.maxByOrNull { row[it] }!! }
            idTensor.close(); maskTensor.close(); result.close()
            val s3Ok = argmax == tc.argmax

            val wordLabels = BankingBioDecoder.collapseToWordLabels(argmax, wordIds.toList())
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
