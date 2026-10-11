package com.summer.ner

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.summer.core.worker.WorkerExecution
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Once-a-day backstop. It doesn't drain itself; it re-triggers the normal backfill path, which
 * dedups (KEEP on the shared backfill name) into any running drain and self-scans for banking rows
 * that were classified but never processed. Emits the same START/END Logcat lines as the coordinator.
 */
@HiltWorker
class NerBackfillBackstopWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val scheduler: NerWorkScheduler,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        WorkerExecution.start(
            workerType = WorkerExecution.TYPE_NER,
            mode = WorkerExecution.MODE_BACKSTOP,
            workRequestId = id.toString(),
            chainId = "-",
            chainIndex = 0,
            runAttempt = runAttemptCount,
        )
        var result: Result = Result.success()
        var exitCause = WorkerExecution.CAUSE_COMPLETED
        try {
            scheduler.enqueueBackfill()
            return result
        } catch (cancelled: CancellationException) {
            result = Result.retry()
            exitCause = WorkerExecution.CAUSE_STOPPED
            throw cancelled
        } finally {
            withContext(NonCancellable) {
                val wasStopped = isStopped
                WorkerExecution.end(
                    workRequestId = id.toString(),
                    result = resultName(result),
                    exitCause = if (wasStopped) WorkerExecution.CAUSE_STOPPED else exitCause,
                    wasStopped = wasStopped,
                    stopReason = if (wasStopped) currentStopReason() else null,
                    itemsProcessed = 0,
                )
            }
        }
    }

    private fun resultName(result: Result): String = when (result) {
        is Result.Success -> WorkerExecution.RESULT_SUCCESS
        is Result.Retry -> WorkerExecution.RESULT_RETRY
        else -> WorkerExecution.RESULT_FAILURE
    }

    private fun currentStopReason(): Int? = try {
        stopReason
    } catch (_: Throwable) {
        null
    }

    companion object {
        private const val WORK_NAME = "production_mobilebert_ner_backfill_periodic"

        /** Registers the daily sweep. Idempotent (KEEP): safe to call on every app start. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NerBackfillBackstopWorker>(1, TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
