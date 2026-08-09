package com.summer.core.android.sms.service

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.summer.core.android.notification.AppNotificationManager
import com.summer.core.android.notification.AppNotificationManager.Companion.NOTIFICATION_ID_SMS_PROCESSING
import com.summer.core.android.sms.model.SmsProcessingError
import com.summer.core.android.sms.model.SmsProcessingStatus
import com.summer.core.classifier.ClassifySmsUseCase
import com.summer.core.domain.model.SmsBatchResult
import com.summer.core.worker.WorkerExecution
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.UUID

@HiltWorker
class SmsProcessingWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val classifySmsUseCase: ClassifySmsUseCase,
    private val appNotificationManager: AppNotificationManager,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "========== SMS PROCESSING STARTED ==========")
        Log.d(TAG, "Start time: $startTime")

        WorkerExecution.start(
            workerType = WorkerExecution.TYPE_SMS_BATCH,
            mode = null,
            workRequestId = id.toString(),
            chainId = UUID.randomUUID().toString(),
            chainIndex = 0,
            runAttempt = runAttemptCount,
        )
        var ranAsForeground = false
        var totalMessages = 0
        var outcome: Result = Result.failure()
        var exitCause: String = WorkerExecution.CAUSE_FAILURE
        var failureCode: String? = null
        var failureMessage: String? = null

        try {
            try {
                setForeground(createForegroundInfo("Starting SMS sync..."))
                ranAsForeground = true
            } catch (e: Exception) {
                // Handle Android 12+ background service restrictions
                // This can happen if the app moves to background before foreground is set
                Log.w(TAG, "Could not set foreground: ${e.message}")
            }
            WorkerExecution.foreground(id.toString(), ranAsForeground)

            val finalResult = classifySmsUseCase { processed: Int, total: Int ->
                totalMessages = total

                // Update notification on each batch
                appNotificationManager.updateNotificationForSmsProcessing(
                    appContext,
                    "$processed/$total messages processed"
                )

                // Update WorkManager progress
                setProgress(
                    workDataOf(
                        SmsProcessingStatus.STATUS_KEY to SmsProcessingStatus.Loading.key,
                        SmsProcessingStatus.PROCESSED_COUNT_KEY to processed,
                        SmsProcessingStatus.TOTAL_COUNT_KEY to total
                    )
                )
            }

            val endTime = System.currentTimeMillis()
            val totalDuration = endTime - startTime
            val avgRate = if (totalDuration > 0) (totalMessages * 1000L / totalDuration) else 0

            Log.d(TAG, "========== SMS PROCESSING COMPLETED ==========")
            Log.d(TAG, "Total duration: ${totalDuration}ms (${totalDuration / 1000}s)")
            Log.d(TAG, "Total messages: $totalMessages")
            Log.d(TAG, "Average rate: $avgRate msgs/sec")
            Log.d(TAG, "===============================================")

            outcome = getWorkerResult(finalResult)
            if (finalResult is SmsBatchResult.Failure) {
                val error = SmsProcessingError.fromException(finalResult.exception)
                failureCode = finalResult.exception.javaClass.simpleName
                failureMessage = error.userMessage
            } else {
                exitCause = WorkerExecution.CAUSE_COMPLETED
            }
            return outcome
        } catch (cancelled: CancellationException) {
            outcome = Result.retry()
            exitCause = WorkerExecution.CAUSE_STOPPED
            throw cancelled
        } catch (e: Exception) {
            val endTime = System.currentTimeMillis()
            Log.e(TAG, "========== SMS PROCESSING FAILED ==========")
            Log.e(TAG, "Duration before failure: ${endTime - startTime}ms")
            Log.e(TAG, "Error: ${e.message}")

            val error = SmsProcessingError.fromException(e)
            failureCode = e.javaClass.simpleName
            failureMessage = error.userMessage
            exitCause = WorkerExecution.CAUSE_FAILURE
            outcome = if (shouldRetry(e)) Result.retry() else Result.failure(workDataOf("error" to error.userMessage))
            setProgress(
                workDataOf(
                    SmsProcessingStatus.STATUS_KEY to SmsProcessingStatus.Error.key,
                    SmsProcessingStatus.ERROR_MESSAGE_KEY to error.userMessage
                )
            )
            return outcome
        } finally {
            withContext(NonCancellable) {
                val wasStopped = isStopped
                WorkerExecution.end(
                    workRequestId = id.toString(),
                    result = resultName(outcome),
                    exitCause = if (wasStopped) WorkerExecution.CAUSE_STOPPED else exitCause,
                    wasStopped = wasStopped,
                    stopReason = if (wasStopped) currentStopReason() else null,
                    itemsProcessed = totalMessages,
                    itemsTotal = if (totalMessages > 0) totalMessages else null,
                    failureCode = failureCode,
                    failureMessage = failureMessage,
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

    private suspend fun getWorkerResult(finalResult: SmsBatchResult): Result {
        return when(finalResult){
            is SmsBatchResult.Success -> {
                setProgress(workDataOf(SmsProcessingStatus.STATUS_KEY to SmsProcessingStatus.Success.key))
                Result.success()
            }
            is SmsBatchResult.Failure -> {
                val error = SmsProcessingError.fromException(finalResult.exception)
                if (shouldRetry(finalResult.exception)) {
                    setProgress(
                        workDataOf(
                            SmsProcessingStatus.STATUS_KEY to SmsProcessingStatus.Error.key,
                            SmsProcessingStatus.ERROR_MESSAGE_KEY to error.userMessage
                        )
                    )
                    Result.retry()
                } else {
                    setProgress(
                        workDataOf(
                            SmsProcessingStatus.STATUS_KEY to SmsProcessingStatus.Error.key,
                            SmsProcessingStatus.ERROR_MESSAGE_KEY to error.userMessage
                        )
                    )
                    Result.failure(workDataOf("error" to error.userMessage))
                }
            }
        }
    }

    private fun shouldRetry(exception: Throwable): Boolean {
        return when (exception) {
            is SecurityException,
            is IllegalStateException,
            is RuntimeException -> runAttemptCount < MAX_RETRY_ATTEMPTS
            else -> false
        }
    }

    private fun createForegroundInfo(contentText: String): ForegroundInfo {
        val notification =
            appNotificationManager.showNotificationForSmsProcessing(appContext, contentText)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ForegroundInfo(NOTIFICATION_ID_SMS_PROCESSING, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
         else
            ForegroundInfo(NOTIFICATION_ID_SMS_PROCESSING, notification)
    }

    companion object {
        private const val TAG = "SmsWorker"
        private const val MAX_RETRY_ATTEMPTS = 3
    }
}
