package com.summer.notifai.nerbenchmark

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.summer.notifai.BuildConfig
import com.summer.notifai.nerbenchmark.db.BenchmarkCaseResultEntity
import com.summer.notifai.nerbenchmark.db.BenchmarkDao
import com.summer.notifai.nerbenchmark.db.BenchmarkNamedEntityEntity
import com.summer.notifai.nerbenchmark.db.BenchmarkRunEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.max

@HiltWorker
class NerBenchmarkWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val dao: BenchmarkDao,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val mode = BenchmarkMode.from(inputData.getString(INPUT_MODE))
        setForeground(createForegroundInfo(
            if (mode == BenchmarkMode.BACKGROUND_PERFORMANCE) "Waiting for app to enter background"
            else "Preparing foreground accuracy benchmark"
        ))
        val backgroundVerified = mode == BenchmarkMode.BACKGROUND_PERFORMANCE && waitUntilBackground()
        if (mode == BenchmarkMode.BACKGROUND_PERFORMANCE && !backgroundVerified) {
            return Result.failure(workDataOf("error" to "App remained visible"))
        }
        setForeground(createForegroundInfo("Preparing benchmark"))

        val battery = appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val power = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val startBattery = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val startCharge = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val startThermal = thermalStatus(power)
        if (startBattery < MIN_BATTERY_PERCENT) {
            return Result.failure(workDataOf("error" to "Battery below $MIN_BATTERY_PERCENT%"))
        }
        if (startThermal >= PowerManager.THERMAL_STATUS_SEVERE) {
            return Result.failure(workDataOf("error" to "Thermal status is severe"))
        }

        val runId = "${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"
        var run: BenchmarkRunEntity? = null
        return try {
            val assets = withContext(Dispatchers.IO) { BenchmarkAssets(appContext).verifyAndMaterialize() }
            run = BenchmarkRunEntity(
                run_id = runId,
                protocol_version = mode.protocol,
                benchmark_mode = mode.value,
                expected_cases = mode.expectedCases,
                selection_sha256 = if (mode == BenchmarkMode.BACKGROUND_PERFORMANCE) {
                    assets.hashes.getValue(BenchmarkAssets.PERFORMANCE_SELECTION)
                } else {
                    null
                },
                model_id = BuildConfig.NER_BENCHMARK_MODEL_ID,
                status = "RUNNING",
                started_at_ms = System.currentTimeMillis(),
                background_verified = backgroundVerified,
                model_sha256 = assets.hashes.getValue(BenchmarkAssets.MODEL),
                tokenizer_sha256 = assets.hashes.getValue(BenchmarkAssets.TOKENIZER),
                fixture_sha256 = assets.hashes.getValue(BenchmarkAssets.FIXTURE),
                golden_sha256 = assets.hashes.getValue(BenchmarkAssets.GOLDEN),
                apk_sha256 = sha256(File(appContext.applicationInfo.sourceDir)),
                device_id = sha256(Build.FINGERPRINT.toByteArray()).take(16),
                device_fingerprint = Build.FINGERPRINT,
                device_model = "${Build.MANUFACTURER}-${Build.MODEL}",
                sdk_int = Build.VERSION.SDK_INT,
                abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
                start_pss_kb = Debug.getPss().toLong(),
                start_heap_kb = usedHeapKb(),
                start_battery_pct = startBattery,
                start_charge_uah = startCharge,
                start_thermal_status = startThermal,
                max_thermal_status = startThermal,
            )
            dao.insertRun(run)

            var currentRun = run
            var maxThermal = startThermal
            var persistenceMs = 0.0
            val wallStart = System.nanoTime()
            val output = withContext(Dispatchers.Default) {
                NerOnnxBenchmarkRunner(appContext).run(
                    assets = assets,
                    mode = mode,
                    onBatch = { batch, total ->
                        maxThermal = max(maxThermal, thermalStatus(power))
                        val updatedRun = withContext(Dispatchers.IO) {
                            val caseEntities = batch.measurements.map { it.toEntity(runId) }
                            val existing = dao.getCases(runId)
                            val allCases = existing + caseEntities
                            val checkpoint = summarizeBenchmarkRun(
                                base = requireNotNull(currentRun),
                                cases = allCases,
                                status = "RUNNING",
                                modelLoadMs = batch.modelLoadMs,
                                computeElapsedMs = batch.computeElapsedMs,
                                wallElapsedMs = elapsedMs(wallStart),
                                persistenceMs = persistenceMs,
                                peakPssKb = batch.peakPssKb,
                                peakHeapKb = batch.peakHeapKb,
                                maxThermal = maxThermal,
                            )
                            val persistenceStart = System.nanoTime()
                            dao.checkpoint(
                                checkpoint,
                                caseEntities,
                                batch.entities.map { it.toEntity(runId) },
                            )
                            persistenceMs += elapsedMs(persistenceStart)
                            summarizeBenchmarkRun(
                                base = checkpoint,
                                cases = allCases,
                                status = "RUNNING",
                                modelLoadMs = batch.modelLoadMs,
                                computeElapsedMs = batch.computeElapsedMs,
                                wallElapsedMs = elapsedMs(wallStart),
                                persistenceMs = persistenceMs,
                                peakPssKb = batch.peakPssKb,
                                peakHeapKb = batch.peakHeapKb,
                                maxThermal = maxThermal,
                            ).also {
                                dao.updateRun(it)
                                BenchmarkExporter(appContext, dao).export(it, live = true)
                            }
                        }
                        currentRun = updatedRun
                        val processed = updatedRun.measured_cases
                        setProgress(workDataOf("processed" to processed, "total" to total))
                        setForeground(createForegroundInfo(
                            "$processed/$total p50=${"%.0f".format(updatedRun.total_p50_ms)}ms " +
                                "wall=${"%.2f".format(updatedRun.wall_throughput_msgs_sec)}/s " +
                                "F1=${"%.3f".format(updatedRun.f1)} " +
                                "fail=${updatedRun.failures} parity=${updatedRun.entity_parity_failures}"
                        ))
                    },
                    isStopped = { isStopped },
                )
            }
            val exported = withContext(Dispatchers.IO) {
                val completed = summarizeBenchmarkRun(
                    base = requireNotNull(currentRun),
                    cases = dao.getCases(runId),
                    status = "COMPLETED",
                    modelLoadMs = output.modelLoadMs,
                    computeElapsedMs = requireNotNull(currentRun).compute_elapsed_ms,
                    wallElapsedMs = elapsedMs(wallStart),
                    persistenceMs = persistenceMs,
                    peakPssKb = output.peakPssKb,
                    peakHeapKb = output.peakHeapKb,
                    maxThermal = maxThermal,
                ).copy(
                    ended_at_ms = System.currentTimeMillis(),
                    end_pss_kb = Debug.getPss().toLong(),
                    end_heap_kb = usedHeapKb(),
                    end_battery_pct = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
                    end_charge_uah = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER),
                    end_thermal_status = thermalStatus(power),
                )
                dao.updateRun(completed)
                BenchmarkExporter(appContext, dao).export(completed)
            }
            Result.success(
                workDataOf(
                    "run_id" to runId,
                    "summary_path" to exported.summary.absolutePath,
                    "checksum_path" to exported.checksum.absolutePath,
                )
            )
        } catch (error: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) {
                run?.let { initial ->
                    val persisted = dao.getRun(runId) ?: initial
                    val failed = persisted.copy(
                        status = if (isStopped || error is CancellationException) "CANCELLED" else "FAILED",
                        ended_at_ms = System.currentTimeMillis(),
                        error = error.stackTraceToString().take(8_000),
                    )
                    dao.updateRun(failed)
                    BenchmarkExporter(appContext, dao).export(failed, live = true)
                }
            }
            Result.failure(workDataOf("run_id" to runId, "error" to (error.message ?: error.javaClass.name)))
        }
    }

    private suspend fun waitUntilBackground(): Boolean {
        repeat(60) {
            if (!isAppVisible()) return true
            delay(1_000)
        }
        return false
    }

    private fun isAppVisible(): Boolean {
        return ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }

    private fun thermalStatus(power: PowerManager): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) power.currentThermalStatus else 0

    private fun usedHeapKb(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / 1024
    }

    private fun elapsedMs(startNs: Long): Double = (System.nanoTime() - startNs) / 1_000_000.0

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

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun createForegroundInfo(text: String): ForegroundInfo {
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, "NER benchmark", NotificationManager.IMPORTANCE_LOW).apply {
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle("ONNX NER benchmark")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun BenchmarkCaseMeasurement.toEntity(runId: String) = BenchmarkCaseResultEntity(
        run_id = runId,
        fixture_id = fixtureId,
        tokenizer_ms = tokenizerMs,
        inference_ms = inferenceMs,
        decode_ms = decodeMs,
        total_ms = totalMs,
        tokenizer_parity = tokenizerParity,
        argmax_parity = argmaxParity,
        entity_parity = entityParity,
        true_positive = truePositive,
        false_positive = falsePositive,
        false_negative = falseNegative,
        error = error,
    )

    private fun BenchmarkStoredEntity.toEntity(runId: String) = BenchmarkNamedEntityEntity(
        run_id = runId,
        fixture_id = fixtureId,
        expected = expected,
        entity_type = entity.type,
        text = entity.text,
        start_token = entity.start,
        end_token = entity.end,
    )

    companion object {
        const val UNIQUE_WORK_NAME = "ner_v50_benchmark"
        const val ACTION_RUN = "com.utilities.msgsense.action.RUN_NER_BENCHMARK"
        const val INPUT_MODE = "benchmark_mode"
        private const val CHANNEL_ID = "ner_benchmark"
        private const val NOTIFICATION_ID = 22050
        private const val MIN_BATTERY_PERCENT = 30
    }
}
