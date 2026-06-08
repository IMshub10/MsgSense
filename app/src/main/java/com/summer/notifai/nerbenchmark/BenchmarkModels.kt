package com.summer.notifai.nerbenchmark

import com.google.gson.annotations.SerializedName

const val BACKGROUND_PERFORMANCE_PROTOCOL = "ner-v50-bg500-v1"
const val FOREGROUND_ACCURACY_PROTOCOL = "ner-v50-fg-accuracy-v1"
const val BENCHMARK_FULL_CASES = 6445
const val BENCHMARK_PERFORMANCE_CASES = 500
const val BENCHMARK_WARMUP_CASES = 25
const val BENCHMARK_CHECKPOINT_CASES = 100

enum class BenchmarkMode(val value: String, val protocol: String, val expectedCases: Int) {
    BACKGROUND_PERFORMANCE("background-performance", BACKGROUND_PERFORMANCE_PROTOCOL, BENCHMARK_PERFORMANCE_CASES),
    FOREGROUND_ACCURACY("foreground-accuracy", FOREGROUND_ACCURACY_PROTOCOL, BENCHMARK_FULL_CASES);

    companion object {
        fun from(value: String?): BenchmarkMode =
            entries.firstOrNull { it.value == value } ?: BACKGROUND_PERFORMANCE
    }
}

data class BenchmarkSelection(
    val seed: String,
    @SerializedName("fixture_ids") val fixtureIds: List<Int>,
)

data class BenchmarkFixture(
    val id: Int,
    val tokens: List<String>,
    val labels: List<String>,
)

data class BenchmarkGolden(
    val id: Int,
    @SerializedName("input_ids") val inputIds: List<Int>,
    @SerializedName("attention_mask") val attentionMask: List<Int>,
    @SerializedName("token_type_ids") val tokenTypeIds: List<Int>,
    @SerializedName("word_ids") val wordIds: List<Int>,
    val argmax: List<Int>,
    @SerializedName("word_labels") val wordLabels: List<String>,
    val entities: List<BenchmarkNamedEntity>,
)

data class BenchmarkNamedEntity(
    val type: String,
    val text: String,
    val start: Int,
    val end: Int,
)

data class BenchmarkCaseMeasurement(
    val fixtureId: Int,
    val tokenizerMs: Double,
    val inferenceMs: Double,
    val decodeMs: Double,
    val totalMs: Double,
    val tokenizerParity: Boolean,
    val argmaxParity: Boolean,
    val entityParity: Boolean,
    val truePositive: Int,
    val falsePositive: Int,
    val falseNegative: Int,
    val error: String? = null,
)

data class BenchmarkRunOutput(
    val modelLoadMs: Double,
    val peakPssKb: Long,
    val peakHeapKb: Long,
)

data class BenchmarkBatch(
    val measurements: List<BenchmarkCaseMeasurement>,
    val entities: List<BenchmarkStoredEntity>,
    val modelLoadMs: Double,
    val computeElapsedMs: Double,
    val peakPssKb: Long,
    val peakHeapKb: Long,
)

data class BenchmarkStoredEntity(
    val fixtureId: Int,
    val expected: Boolean,
    val entity: BenchmarkNamedEntity,
)

fun percentile(values: List<Double>, percentile: Double): Double {
    if (values.isEmpty()) return 0.0
    val sorted = values.sorted()
    val index = ((sorted.size - 1) * percentile).toInt().coerceIn(sorted.indices)
    return sorted[index]
}
