package com.summer.notifai.nerbenchmark.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "ner_benchmark_runs",
    primaryKeys = ["run_id"],
    indices = [Index("model_id"), Index("status")],
)
data class BenchmarkRunEntity(
    val run_id: String,
    val protocol_version: String,
    val benchmark_mode: String,
    val expected_cases: Int,
    val selection_sha256: String?,
    val model_id: String,
    val status: String,
    val started_at_ms: Long,
    val ended_at_ms: Long? = null,
    val background_verified: Boolean,
    val model_sha256: String,
    val tokenizer_sha256: String,
    val fixture_sha256: String,
    val golden_sha256: String,
    val apk_sha256: String,
    val device_id: String,
    val device_fingerprint: String,
    val device_model: String,
    val sdk_int: Int,
    val abi: String,
    val measured_cases: Int = 0,
    val failures: Int = 0,
    val tokenizer_parity_failures: Int = 0,
    val entity_parity_failures: Int = 0,
    val argmax_parity_failures: Int = 0,
    val model_load_ms: Double? = null,
    val total_p50_ms: Double? = null,
    val total_p90_ms: Double? = null,
    val total_p95_ms: Double? = null,
    val total_p99_ms: Double? = null,
    val tokenizer_p50_ms: Double? = null,
    val tokenizer_p95_ms: Double? = null,
    val inference_p50_ms: Double? = null,
    val inference_p95_ms: Double? = null,
    val decode_p50_ms: Double? = null,
    val decode_p95_ms: Double? = null,
    val throughput_msgs_sec: Double? = null,
    val true_positive: Int = 0,
    val false_positive: Int = 0,
    val false_negative: Int = 0,
    val precision: Double? = null,
    val recall: Double? = null,
    val f1: Double? = null,
    val persistence_ms: Double? = null,
    val last_checkpoint_at_ms: Long? = null,
    val compute_elapsed_ms: Double = 0.0,
    val wall_elapsed_ms: Double = 0.0,
    val compute_throughput_msgs_sec: Double? = null,
    val wall_throughput_msgs_sec: Double? = null,
    val start_pss_kb: Long,
    val peak_pss_kb: Long? = null,
    val end_pss_kb: Long? = null,
    val start_heap_kb: Long,
    val peak_heap_kb: Long? = null,
    val end_heap_kb: Long? = null,
    val start_battery_pct: Int,
    val end_battery_pct: Int? = null,
    val start_charge_uah: Int,
    val end_charge_uah: Int? = null,
    val start_thermal_status: Int,
    val max_thermal_status: Int,
    val end_thermal_status: Int? = null,
    val error: String? = null,
)

@Entity(
    tableName = "ner_benchmark_case_results",
    primaryKeys = ["run_id", "fixture_id"],
    foreignKeys = [
        ForeignKey(
            entity = BenchmarkRunEntity::class,
            parentColumns = ["run_id"],
            childColumns = ["run_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("run_id"), Index("entity_parity")],
)
data class BenchmarkCaseResultEntity(
    val run_id: String,
    val fixture_id: Int,
    val tokenizer_ms: Double,
    val inference_ms: Double,
    val decode_ms: Double,
    val total_ms: Double,
    val tokenizer_parity: Boolean,
    val argmax_parity: Boolean,
    val entity_parity: Boolean,
    val true_positive: Int,
    val false_positive: Int,
    val false_negative: Int,
    val error: String?,
)

@Entity(
    tableName = "ner_benchmark_entities",
    primaryKeys = ["run_id", "fixture_id", "expected", "entity_type", "start_token", "end_token", "text"],
    foreignKeys = [
        ForeignKey(
            entity = BenchmarkRunEntity::class,
            parentColumns = ["run_id"],
            childColumns = ["run_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("run_id"), Index("fixture_id"), Index("entity_type")],
)
data class BenchmarkNamedEntityEntity(
    val run_id: String,
    val fixture_id: Int,
    val expected: Boolean,
    val entity_type: String,
    val text: String,
    val start_token: Int,
    val end_token: Int,
)
