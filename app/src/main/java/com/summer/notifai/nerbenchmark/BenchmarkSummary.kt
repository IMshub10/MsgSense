package com.summer.notifai.nerbenchmark

import com.summer.notifai.nerbenchmark.db.BenchmarkCaseResultEntity
import com.summer.notifai.nerbenchmark.db.BenchmarkRunEntity

fun summarizeBenchmarkRun(
    base: BenchmarkRunEntity,
    cases: List<BenchmarkCaseResultEntity>,
    status: String,
    modelLoadMs: Double,
    computeElapsedMs: Double,
    wallElapsedMs: Double,
    persistenceMs: Double,
    peakPssKb: Long,
    peakHeapKb: Long,
    maxThermal: Int,
    checkpointAtMs: Long = System.currentTimeMillis(),
): BenchmarkRunEntity {
    val totals = cases.map { it.total_ms }
    val truePositive = cases.sumOf { it.true_positive }
    val falsePositive = cases.sumOf { it.false_positive }
    val falseNegative = cases.sumOf { it.false_negative }
    val precision = truePositive.toDouble() / (truePositive + falsePositive).coerceAtLeast(1)
    val recall = truePositive.toDouble() / (truePositive + falseNegative).coerceAtLeast(1)
    val f1 = if (precision + recall == 0.0) 0.0 else 2 * precision * recall / (precision + recall)
    val computeThroughput = cases.size * 1000.0 / computeElapsedMs.coerceAtLeast(0.001)
    val wallThroughput = cases.size * 1000.0 / wallElapsedMs.coerceAtLeast(0.001)
    return base.copy(
        status = status,
        measured_cases = cases.size,
        failures = cases.count { it.error != null },
        tokenizer_parity_failures = cases.count { !it.tokenizer_parity },
        entity_parity_failures = cases.count { !it.entity_parity },
        argmax_parity_failures = cases.count { !it.argmax_parity },
        model_load_ms = modelLoadMs,
        total_p50_ms = percentile(totals, 0.50),
        total_p90_ms = percentile(totals, 0.90),
        total_p95_ms = percentile(totals, 0.95),
        total_p99_ms = percentile(totals, 0.99),
        tokenizer_p50_ms = percentile(cases.map { it.tokenizer_ms }, 0.50),
        tokenizer_p95_ms = percentile(cases.map { it.tokenizer_ms }, 0.95),
        inference_p50_ms = percentile(cases.map { it.inference_ms }, 0.50),
        inference_p95_ms = percentile(cases.map { it.inference_ms }, 0.95),
        decode_p50_ms = percentile(cases.map { it.decode_ms }, 0.50),
        decode_p95_ms = percentile(cases.map { it.decode_ms }, 0.95),
        throughput_msgs_sec = wallThroughput,
        compute_throughput_msgs_sec = computeThroughput,
        wall_throughput_msgs_sec = wallThroughput,
        compute_elapsed_ms = computeElapsedMs,
        wall_elapsed_ms = wallElapsedMs,
        last_checkpoint_at_ms = checkpointAtMs,
        true_positive = truePositive,
        false_positive = falsePositive,
        false_negative = falseNegative,
        precision = precision,
        recall = recall,
        f1 = f1,
        persistence_ms = persistenceMs,
        peak_pss_kb = peakPssKb,
        peak_heap_kb = peakHeapKb,
        max_thermal_status = maxThermal,
    )
}
