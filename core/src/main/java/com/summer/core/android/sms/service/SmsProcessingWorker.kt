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
import com.summer.core.domain.model.SmsBatchResult
import com.summer.core.domain.repository.ISmsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SmsProcessingWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: ISmsRepository,
    private val appNotificationManager: AppNotificationManager
) : CoroutineWorker(appContext, workerParams) {

    private var retryAttempt = 0

    override suspend fun doWork(): Result {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "========== SMS PROCESSING STARTED ==========")
        Log.d(TAG, "Start time: $startTime")
        
        try {
            try {
                setForeground(createForegroundInfo("Starting SMS sync..."))
            } catch (e: Exception) {
                // Handle Android 12+ background service restrictions
                // This can happen if the app moves to background before foreground is set
                Log.w(TAG, "Could not set foreground: ${e.message}")
            }
            var totalMessages = 0
            
            val finalResult = repository.fetchSmsMessagesFromDevice { processed: Int, total: Int ->
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
            
            return getWorkerResult(finalResult)
        } catch (e: Exception) {
            val endTime = System.currentTimeMillis()
            Log.e(TAG, "========== SMS PROCESSING FAILED ==========")
            Log.e(TAG, "Duration before failure: ${endTime - startTime}ms")
            Log.e(TAG, "Error: ${e.message}")
            
            val error = SmsProcessingError.fromException(e)
            if (shouldRetry(e)) {
                setProgress(
                    workDataOf(
                        SmsProcessingStatus.STATUS_KEY to SmsProcessingStatus.Error.key,
                        SmsProcessingStatus.ERROR_MESSAGE_KEY to error.userMessage
                    )
                )
                return Result.retry()
            }
            setProgress(
                workDataOf(
                    SmsProcessingStatus.STATUS_KEY to SmsProcessingStatus.Error.key,
                    SmsProcessingStatus.ERROR_MESSAGE_KEY to error.userMessage
                )
            )
            return Result.failure(workDataOf("error" to error.userMessage))
        }
    }

    private suspend fun getWorkerResult(finalResult: SmsBatchResult): Result {
        return when(finalResult){
            is SmsBatchResult.Success -> {
                repository.setSmsProcessingStatusCompleted(true)
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
            is RuntimeException -> retryAttempt++ < MAX_RETRY_ATTEMPTS
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