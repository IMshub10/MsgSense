package com.summer.notifai.nerbenchmark

import android.content.Context
import android.system.Os
import com.google.gson.GsonBuilder
import com.summer.notifai.nerbenchmark.db.BenchmarkCaseResultEntity
import com.summer.notifai.nerbenchmark.db.BenchmarkDao
import com.summer.notifai.nerbenchmark.db.BenchmarkRunEntity
import java.io.File
import java.security.MessageDigest

class BenchmarkExporter(
    private val context: Context,
    private val dao: BenchmarkDao,
) {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun export(run: BenchmarkRunEntity, live: Boolean = false): ExportedRun {
        val root = requireNotNull(context.getExternalFilesDir("ner-benchmark-exports"))
        val directory = File(
            root,
            "${run.protocol_version}/${run.device_id}/${run.model_id}",
        ).apply { mkdirs() }
        val suffix = if (live) ".live" else ""
        val summary = File(directory, "${run.run_id}$suffix.json")
        val cases = File(directory, "${run.run_id}$suffix-cases.csv")
        val checksum = File(directory, "${run.run_id}$suffix.sha256")

        val caseRows = dao.getCases(run.run_id)
        atomicWrite(cases) { writer ->
            writer.appendLine(
                "fixture_id,tokenizer_ms,inference_ms,decode_ms,total_ms,argmax_parity," +
                    "tokenizer_parity,entity_parity,true_positive,false_positive,false_negative,error"
            )
            caseRows.forEach { row -> writer.appendLine(row.toCsv()) }
        }
        val summaryData = linkedMapOf<String, Any?>(
            "protocol_version" to run.protocol_version,
            "benchmark_mode" to run.benchmark_mode,
            "expected_cases" to run.expected_cases,
            "selection_sha256" to run.selection_sha256,
            "run_id" to run.run_id,
            "model_id" to run.model_id,
            "status" to run.status,
            "started_at_ms" to run.started_at_ms,
            "ended_at_ms" to run.ended_at_ms,
            "measured_cases" to run.measured_cases,
            "cases_file" to cases.name,
            "model_sha256" to run.model_sha256,
            "tokenizer_sha256" to run.tokenizer_sha256,
            "fixture_sha256" to run.fixture_sha256,
            "golden_sha256" to run.golden_sha256,
            "apk_sha256" to run.apk_sha256,
            "device_id" to run.device_id,
            "device_fingerprint" to run.device_fingerprint,
            "device_model" to run.device_model,
            "sdk_int" to run.sdk_int,
            "abi" to run.abi,
            "background_verified" to run.background_verified,
            "model_load_ms" to run.model_load_ms,
            "total_p50_ms" to run.total_p50_ms,
            "total_p90_ms" to run.total_p90_ms,
            "total_p95_ms" to run.total_p95_ms,
            "total_p99_ms" to run.total_p99_ms,
            "tokenizer_p50_ms" to run.tokenizer_p50_ms,
            "tokenizer_p95_ms" to run.tokenizer_p95_ms,
            "inference_p50_ms" to run.inference_p50_ms,
            "inference_p95_ms" to run.inference_p95_ms,
            "decode_p50_ms" to run.decode_p50_ms,
            "decode_p95_ms" to run.decode_p95_ms,
            "throughput_msgs_sec" to run.throughput_msgs_sec,
            "true_positive" to run.true_positive,
            "false_positive" to run.false_positive,
            "false_negative" to run.false_negative,
            "precision" to run.precision,
            "recall" to run.recall,
            "f1" to run.f1,
            "persistence_ms" to run.persistence_ms,
            "last_checkpoint_at_ms" to run.last_checkpoint_at_ms,
            "compute_elapsed_ms" to run.compute_elapsed_ms,
            "wall_elapsed_ms" to run.wall_elapsed_ms,
            "compute_throughput_msgs_sec" to run.compute_throughput_msgs_sec,
            "wall_throughput_msgs_sec" to run.wall_throughput_msgs_sec,
            "start_pss_kb" to run.start_pss_kb,
            "peak_pss_kb" to run.peak_pss_kb,
            "end_pss_kb" to run.end_pss_kb,
            "start_heap_kb" to run.start_heap_kb,
            "peak_heap_kb" to run.peak_heap_kb,
            "end_heap_kb" to run.end_heap_kb,
            "start_battery_pct" to run.start_battery_pct,
            "end_battery_pct" to run.end_battery_pct,
            "start_charge_uah" to run.start_charge_uah,
            "end_charge_uah" to run.end_charge_uah,
            "start_thermal_status" to run.start_thermal_status,
            "max_thermal_status" to run.max_thermal_status,
            "end_thermal_status" to run.end_thermal_status,
            "failures" to run.failures,
            "tokenizer_parity_failures" to run.tokenizer_parity_failures,
            "entity_parity_failures" to run.entity_parity_failures,
            "argmax_parity_failures" to run.argmax_parity_failures,
        )
        atomicWrite(summary) { it.write(gson.toJson(summaryData)) }
        atomicWrite(checksum) { writer -> writer.write(
            "${sha256(summary)}  ${summary.name}\n" +
                "${sha256(cases)}  ${cases.name}\n"
        ) }
        return ExportedRun(summary, cases, checksum)
    }

    private inline fun atomicWrite(target: File, write: (java.io.BufferedWriter) -> Unit) {
        val temporary = File(target.parentFile, "${target.name}.tmp")
        temporary.bufferedWriter().use(write)
        Os.rename(temporary.absolutePath, target.absolutePath)
    }

    private fun BenchmarkCaseResultEntity.toCsv(): String = listOf(
        fixture_id,
        tokenizer_ms,
        inference_ms,
        decode_ms,
        total_ms,
        argmax_parity,
        tokenizer_parity,
        entity_parity,
        true_positive,
        false_positive,
        false_negative,
        error.orEmpty(),
    ).joinToString(",") { csv(it.toString()) }

    private fun csv(value: String): String = "\"${value.replace("\"", "\"\"")}\""
    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

data class ExportedRun(val summary: File, val cases: File, val checksum: File)
