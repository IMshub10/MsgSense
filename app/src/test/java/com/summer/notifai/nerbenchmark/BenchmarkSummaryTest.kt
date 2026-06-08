package com.summer.notifai.nerbenchmark

import com.summer.notifai.nerbenchmark.db.BenchmarkCaseResultEntity
import com.summer.notifai.nerbenchmark.db.BenchmarkRunEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class BenchmarkSummaryTest {
    @Test
    fun summarySeparatesComputeAndWallThroughput() {
        val cases = listOf(
            case(1, totalMs = 10.0, truePositive = 2),
            case(2, totalMs = 30.0, entityParity = false, falseNegative = 1),
        )

        val summary = summarizeBenchmarkRun(
            base = run(),
            cases = cases,
            status = "RUNNING",
            modelLoadMs = 12.0,
            computeElapsedMs = 40.0,
            wallElapsedMs = 100.0,
            persistenceMs = 5.0,
            peakPssKb = 200,
            peakHeapKb = 100,
            maxThermal = 1,
            checkpointAtMs = 99,
        )

        assertEquals(2, summary.measured_cases)
        assertEquals(1, summary.entity_parity_failures)
        assertEquals(50.0, summary.compute_throughput_msgs_sec!!, 0.0)
        assertEquals(20.0, summary.wall_throughput_msgs_sec!!, 0.0)
        assertEquals(summary.wall_throughput_msgs_sec, summary.throughput_msgs_sec)
        assertEquals(99L, summary.last_checkpoint_at_ms)
        assertEquals(10.0, summary.total_p50_ms!!, 0.0)
    }

    private fun case(
        id: Int,
        totalMs: Double,
        truePositive: Int = 0,
        falseNegative: Int = 0,
        entityParity: Boolean = true,
    ) = BenchmarkCaseResultEntity(
        run_id = "run",
        fixture_id = id,
        tokenizer_ms = 1.0,
        inference_ms = totalMs - 2.0,
        decode_ms = 1.0,
        total_ms = totalMs,
        tokenizer_parity = true,
        argmax_parity = true,
        entity_parity = entityParity,
        true_positive = truePositive,
        false_positive = 0,
        false_negative = falseNegative,
        error = null,
    )

    private fun run() = BenchmarkRunEntity(
        run_id = "run",
        protocol_version = BACKGROUND_PERFORMANCE_PROTOCOL,
        benchmark_mode = BenchmarkMode.BACKGROUND_PERFORMANCE.value,
        expected_cases = BENCHMARK_PERFORMANCE_CASES,
        selection_sha256 = "s",
        model_id = "albert-v50",
        status = "RUNNING",
        started_at_ms = 1,
        background_verified = true,
        model_sha256 = "m",
        tokenizer_sha256 = "t",
        fixture_sha256 = "f",
        golden_sha256 = "g",
        apk_sha256 = "a",
        device_id = "device-id",
        device_fingerprint = "device",
        device_model = "device",
        sdk_int = 35,
        abi = "arm64-v8a",
        start_pss_kb = 1,
        start_heap_kb = 1,
        start_battery_pct = 100,
        start_charge_uah = 1,
        start_thermal_status = 0,
        max_thermal_status = 0,
    )
}
