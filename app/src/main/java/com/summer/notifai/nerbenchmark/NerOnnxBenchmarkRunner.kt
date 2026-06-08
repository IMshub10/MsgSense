package com.summer.notifai.nerbenchmark

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.os.Debug
import com.google.gson.Gson
import com.summer.notifai.banking_ner.BankingBioDecoder
import com.summer.notifai.banking_ner.HfTokenizerBridge
import java.nio.LongBuffer
import kotlin.math.max

class NerOnnxBenchmarkRunner(private val context: Context) {
    private val gson = Gson()

    suspend fun run(
        assets: MaterializedAssets,
        mode: BenchmarkMode,
        onBatch: suspend (batch: BenchmarkBatch, total: Int) -> Unit,
        isStopped: () -> Boolean,
    ): BenchmarkRunOutput {
        val allFixtures = assets.fixture.useLines { lines ->
            lines.filter { it.isNotBlank() }.map { gson.fromJson(it, BenchmarkFixture::class.java) }.toList()
        }
        val goldens = assets.golden.useLines { lines ->
            lines.filter { it.isNotBlank() }.map { gson.fromJson(it, BenchmarkGolden::class.java) }
                .associateBy { it.id }
        }
        check(allFixtures.size == BENCHMARK_FULL_CASES) {
            "Expected $BENCHMARK_FULL_CASES fixtures, found ${allFixtures.size}"
        }
        check(goldens.size == BENCHMARK_FULL_CASES) {
            "Expected $BENCHMARK_FULL_CASES goldens, found ${goldens.size}"
        }
        val fixtureIds = allFixtures.map { it.id }.toSet()
        check(fixtureIds.size == BENCHMARK_FULL_CASES) { "Fixture IDs must be unique" }
        check(fixtureIds == goldens.keys) { "Fixture and golden IDs differ" }
        val fixtures = when (mode) {
            BenchmarkMode.BACKGROUND_PERFORMANCE -> {
                val selection = gson.fromJson(assets.performanceSelection.readText(), BenchmarkSelection::class.java)
                check(selection.fixtureIds.size == BENCHMARK_PERFORMANCE_CASES) {
                    "Expected $BENCHMARK_PERFORMANCE_CASES selected fixtures, found ${selection.fixtureIds.size}"
                }
                check(selection.fixtureIds.toSet().size == BENCHMARK_PERFORMANCE_CASES) {
                    "Performance fixture IDs must be unique"
                }
                val byId = allFixtures.associateBy { it.id }
                selection.fixtureIds.map { id -> checkNotNull(byId[id]) { "Unknown selected fixture ID $id" } }
            }
            BenchmarkMode.FOREGROUND_ACCURACY -> allFixtures
        }
        check(fixtures.size == mode.expectedCases)

        val loadStart = System.nanoTime()
        HfTokenizerBridge.loadLibrary()
        HfTokenizerBridge.loadTokenizer(assets.tokenizer.absolutePath)
        val environment = OrtEnvironment.getEnvironment()
        val session = environment.createSession(assets.model.absolutePath, OrtSession.SessionOptions())
        val modelLoadMs = elapsedMs(loadStart)

        try {
            fixtures.take(BENCHMARK_WARMUP_CASES).forEach { fixture ->
                infer(session, environment, fixture, goldens.getValue(fixture.id))
            }

            val measurements = ArrayList<BenchmarkCaseMeasurement>(BENCHMARK_CHECKPOINT_CASES)
            val storedEntities = ArrayList<BenchmarkStoredEntity>()
            var computeElapsedMs = 0.0
            var peakPssKb = Debug.getPss().toLong()
            var peakHeapKb = usedHeapKb()

            for ((index, fixture) in fixtures.withIndex()) {
                check(!isStopped()) { "Benchmark cancelled" }
                val golden = goldens.getValue(fixture.id)
                storedEntities += namedEntities(
                    fixture.tokens.take(golden.wordLabels.size),
                    fixture.labels.take(golden.wordLabels.size),
                )
                    .map { BenchmarkStoredEntity(fixture.id, true, it) }
                try {
                    val result = infer(session, environment, fixture, golden)
                    measurements += result.first
                    computeElapsedMs += result.first.totalMs
                    storedEntities += result.second.map { BenchmarkStoredEntity(fixture.id, false, it) }
                } catch (error: Exception) {
                    val failed = BenchmarkCaseMeasurement(
                        fixtureId = fixture.id,
                        tokenizerMs = 0.0,
                        inferenceMs = 0.0,
                        decodeMs = 0.0,
                        totalMs = 0.0,
                        tokenizerParity = false,
                        argmaxParity = false,
                        entityParity = false,
                        truePositive = 0,
                        falsePositive = 0,
                        falseNegative = namedEntities(
                            fixture.tokens.take(golden.wordLabels.size),
                            fixture.labels.take(golden.wordLabels.size),
                        ).size,
                        error = error.stackTraceToString().take(4_000),
                    )
                    measurements += failed
                    computeElapsedMs += failed.totalMs
                }
                if (measurements.size == BENCHMARK_CHECKPOINT_CASES || index == fixtures.lastIndex) {
                    peakPssKb = max(peakPssKb, Debug.getPss().toLong())
                    peakHeapKb = max(peakHeapKb, usedHeapKb())
                    onBatch(
                        BenchmarkBatch(
                            measurements = measurements.toList(),
                            entities = storedEntities.toList(),
                            modelLoadMs = modelLoadMs,
                            computeElapsedMs = computeElapsedMs,
                            peakPssKb = peakPssKb,
                            peakHeapKb = peakHeapKb,
                        ),
                        fixtures.size,
                    )
                    measurements.clear()
                    storedEntities.clear()
                }
            }
            return BenchmarkRunOutput(modelLoadMs, peakPssKb, peakHeapKb)
        } finally {
            session.close()
        }
    }

    private fun infer(
        session: OrtSession,
        environment: OrtEnvironment,
        fixture: BenchmarkFixture,
        golden: BenchmarkGolden,
    ): Pair<BenchmarkCaseMeasurement, List<BenchmarkNamedEntity>> {
        val totalStart = System.nanoTime()
        val tokenizerStart = System.nanoTime()
        val tokenized = HfTokenizerBridge.encode(fixture.tokens)
        val tokenizerMs = elapsedMs(tokenizerStart)

        val inputs = mutableMapOf<String, OnnxTensor>()
        fun tensor(values: IntArray): OnnxTensor = OnnxTensor.createTensor(
            environment,
            LongBuffer.wrap(LongArray(values.size) { values[it].toLong() }),
            longArrayOf(1, values.size.toLong()),
        )
        inputs["input_ids"] = tensor(tokenized.inputIds)
        inputs["attention_mask"] = tensor(tokenized.attentionMask)
        if ("token_type_ids" in session.inputInfo) {
            inputs["token_type_ids"] = tensor(IntArray(tokenized.inputIds.size))
        }

        val inferenceStart = System.nanoTime()
        val argmax = try {
            session.run(inputs).use { result ->
                @Suppress("UNCHECKED_CAST")
                val logits = (result[0].value as Array<Array<FloatArray>>)[0]
                logits.map { row -> row.indices.maxByOrNull { row[it] }!! }
            }
        } finally {
            inputs.values.forEach(OnnxTensor::close)
        }
        val inferenceMs = elapsedMs(inferenceStart)

        val decodeStart = System.nanoTime()
        val wordLabels = BankingBioDecoder.collapseToWordLabels(argmax, tokenized.wordIds.toList())
        val predicted = namedEntities(fixture.tokens, wordLabels)
        val decodeMs = elapsedMs(decodeStart)
        val labeledEntities = namedEntities(
            fixture.tokens.take(wordLabels.size),
            fixture.labels.take(wordLabels.size),
        )
        val expectedSet = labeledEntities.toSet()
        val predictedSet = predicted.toSet()
        val tp = expectedSet.intersect(predictedSet).size

        return BenchmarkCaseMeasurement(
            fixtureId = fixture.id,
            tokenizerMs = tokenizerMs,
            inferenceMs = inferenceMs,
            decodeMs = decodeMs,
            totalMs = elapsedMs(totalStart),
            tokenizerParity = tokenized.inputIds.toList() == golden.inputIds &&
                tokenized.attentionMask.toList() == golden.attentionMask &&
                tokenized.wordIds.toList() == golden.wordIds &&
                golden.tokenTypeIds.all { it == 0 },
            argmaxParity = argmax == golden.argmax,
            entityParity = predicted == golden.entities,
            truePositive = tp,
            falsePositive = predictedSet.size - tp,
            falseNegative = expectedSet.size - tp,
        ) to predicted
    }

    private fun namedEntities(tokens: List<String>, labels: List<String>): List<BenchmarkNamedEntity> {
        val result = mutableListOf<BenchmarkNamedEntity>()
        var start = -1
        var type: String? = null
        fun flush(endExclusive: Int) {
            val currentType = type
            if (start >= 0 && currentType != null) {
                result += BenchmarkNamedEntity(
                    type = currentType,
                    text = tokens.subList(start, endExclusive).joinToString(" "),
                    start = start,
                    end = endExclusive - 1,
                )
            }
            start = -1
            type = null
        }
        for (index in 0..labels.size) {
            val label = labels.getOrElse(index) { "O" }
            when {
                label.startsWith("B-") -> {
                    flush(index)
                    start = index
                    type = label.removePrefix("B-")
                }
                label.startsWith("I-") && label.removePrefix("I-") == type -> Unit
                else -> flush(index)
            }
        }
        return result
    }

    private fun elapsedMs(startNs: Long): Double = (System.nanoTime() - startNs) / 1_000_000.0

    private fun usedHeapKb(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / 1024
    }
}
