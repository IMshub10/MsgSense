package com.summer.notifai.ner

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.summer.core.android.notification.AppNotificationManager
import com.summer.core.data.local.dao.NerDao
import com.summer.core.data.local.entities.SmsNerEntity
import com.summer.core.data.local.model.PendingNerSms
import com.summer.core.ner.NerConstants
import com.summer.core.banking.BankAccountOrganizer
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@HiltWorker
class NerCoordinatorWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val dao: NerDao,
    private val scheduler: NerWorkScheduler,
    private val notificationManager: AppNotificationManager,
    private val accountOrganizer: BankAccountOrganizer,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val mode = inputData.getString(INPUT_MODE) ?: NerWorkScheduler.MODE_BACKFILL
        dao.recoverInterrupted(
            if (mode == NerWorkScheduler.MODE_REALTIME) NerConstants.PRIORITY_REALTIME
            else NerConstants.PRIORITY_BACKFILL,
            System.currentTimeMillis(),
        )
        return if (mode == NerWorkScheduler.MODE_REALTIME) {
            processRealtime()
        } else {
            processBackfill()
        }
    }

    private suspend fun processRealtime(): Result {
        val pending = dao.nextPendingForPriority(NerConstants.PRIORITY_REALTIME)
            ?: return Result.success()
        val notificationId = pending.notificationId ?: notificationIdFor(pending.extractionId)
        var notificationVisible = prepareRealtimeNotification(pending, notificationId)
        if (dao.markRunning(pending.extractionId, System.currentTimeMillis()) == 0) {
            if (notificationVisible) notificationManager.cancelBankingNotification(notificationId)
            dao.clearNotificationState(pending.extractionId, System.currentTimeMillis())
            return Result.success()
        }

        val client = NerServiceClient(appContext)
        return try {
            val result = withTimeout(NerConstants.REALTIME_TIMEOUT_MS) {
                client.extract(pending.smsId, pending.rawAddress, pending.body)
            }
            dao.complete(
                extractionId = pending.extractionId,
                entities = result.entities.mapIndexed { index, entity ->
                    SmsNerEntity(
                        extractionId = pending.extractionId,
                        entityOrder = index,
                        entityType = entity.type,
                        rawText = entity.rawText,
                        normalizedValue = entity.normalizedValue,
                        startOffset = entity.startOffset,
                        endOffset = entity.endOffset,
                    )
                },
                modelId = result.modelId,
                modelSha256 = result.modelSha256,
                tokenizerSha256 = result.tokenizerSha256,
                preprocessingVersion = result.preprocessingVersion,
                tokenCount = result.tokenCount,
                truncated = result.truncated,
                inferenceMs = result.inferenceMs,
                notificationState = if (notificationVisible) NerConstants.NOTIFICATION_STATE_READY
                else NerConstants.NOTIFICATION_STATE_SUPPRESSED,
                now = System.currentTimeMillis(),
            )
            accountOrganizer.organizePending()
            if (notificationVisible) {
                scheduler.enqueueResultNotification(pending.extractionId, id)
            }
            scheduler.enqueueNextRealtime()
            Result.success()
        } catch (timeout: TimeoutCancellationException) {
            notificationManager.cancelBankingNotification(notificationId)
            dao.clearNotificationState(pending.extractionId, System.currentTimeMillis())
            val attempts = pending.attempts + 1
            if (attempts >= NerConstants.MAX_ATTEMPTS) {
                dao.markTerminalFailure(
                    pending.extractionId, System.currentTimeMillis(), "REALTIME_TIMEOUT", null,
                )
                scheduler.enqueueNextRealtime()
                Result.success()
            } else {
                dao.markPendingFailure(
                    pending.extractionId, System.currentTimeMillis(), "REALTIME_TIMEOUT", null,
                )
                Result.retry()
            }
        } catch (cancelled: CancellationException) {
            notificationManager.cancelBankingNotification(notificationId)
            withContext(NonCancellable) {
                val now = System.currentTimeMillis()
                dao.clearNotificationState(pending.extractionId, now)
                dao.markPendingFailure(pending.extractionId, now, "WORK_CANCELLED", null)
            }
            throw cancelled
        } catch (error: Throwable) {
            notificationManager.cancelBankingNotification(notificationId)
            dao.clearNotificationState(pending.extractionId, System.currentTimeMillis())
            val attempts = pending.attempts + 1
            val code = error.javaClass.simpleName
            if (attempts >= NerConstants.MAX_ATTEMPTS) {
                dao.markTerminalFailure(
                    pending.extractionId, System.currentTimeMillis(), code, error.message?.take(200),
                )
                scheduler.enqueueNextRealtime()
                Result.success()
            } else {
                dao.markPendingFailure(
                    pending.extractionId, System.currentTimeMillis(), code, error.message?.take(200),
                )
                Result.retry()
            }
        } finally {
            client.close()
        }
    }

    private suspend fun prepareRealtimeNotification(pending: PendingNerSms, notificationId: Int): Boolean {
        val now = System.currentTimeMillis()
        if (pending.isBlocked) {
            dao.updateNotificationState(
                pending.extractionId, notificationId, NerConstants.NOTIFICATION_STATE_SUPPRESSED,
                NerConstants.SUPPRESSION_BLOCKED_SENDER, now,
            )
            return false
        }
        if (!notificationManager.canShowBankingNotifications()) {
            dao.updateNotificationState(
                pending.extractionId, notificationId, NerConstants.NOTIFICATION_STATE_SUPPRESSED,
                NerConstants.SUPPRESSION_NOTIFICATIONS_DISABLED, now,
            )
            return false
        }
        dao.updateNotificationState(
            pending.extractionId, notificationId, NerConstants.NOTIFICATION_STATE_PROGRESS, null, now,
        )
        return try {
            setForeground(createForegroundInfo(notificationId))
            true
        } catch (_: Exception) {
            notificationManager.cancelBankingNotification(notificationId)
            dao.updateNotificationState(
                pending.extractionId, notificationId, NerConstants.NOTIFICATION_STATE_SUPPRESSED,
                NerConstants.SUPPRESSION_FOREGROUND_REJECTED, System.currentTimeMillis(),
            )
            false
        }
    }

    private suspend fun processBackfill(): Result {
        dao.enqueueMissingBackfill(System.currentTimeMillis())
        val client = NerServiceClient(appContext)
        val started = System.currentTimeMillis()
        var processed = 0
        try {
            while (!isStopped &&
                processed < NerConstants.BACKFILL_CHUNK_SIZE &&
                System.currentTimeMillis() - started < NerConstants.BACKFILL_CHUNK_DURATION_MS
            ) {
                if (dao.hasRealtimeWork()) break
                val pending = dao.nextPendingForPriority(NerConstants.PRIORITY_BACKFILL) ?: break
                if (dao.markRunning(pending.extractionId, System.currentTimeMillis()) == 0) continue
                try {
                    val result = client.extract(pending.smsId, pending.rawAddress, pending.body)
                    dao.complete(
                        pending.extractionId,
                        result.entities.mapIndexed { index, entity ->
                            SmsNerEntity(
                                extractionId = pending.extractionId,
                                entityOrder = index,
                                entityType = entity.type,
                                rawText = entity.rawText,
                                normalizedValue = entity.normalizedValue,
                                startOffset = entity.startOffset,
                                endOffset = entity.endOffset,
                            )
                        },
                        result.modelId, result.modelSha256, result.tokenizerSha256,
                        result.preprocessingVersion, result.tokenCount, result.truncated,
                        result.inferenceMs, NerConstants.NOTIFICATION_STATE_NONE,
                        System.currentTimeMillis(),
                    )
                    accountOrganizer.organizePending()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    val attempts = pending.attempts + 1
                    if (attempts >= NerConstants.MAX_ATTEMPTS) {
                        dao.markTerminalFailure(
                            pending.extractionId, System.currentTimeMillis(),
                            error.javaClass.simpleName, error.message?.take(200),
                        )
                    } else {
                        dao.markPendingFailure(
                            pending.extractionId, System.currentTimeMillis(),
                            error.javaClass.simpleName, error.message?.take(200),
                        )
                    }
                }
                processed++
            }
        } finally {
            client.close()
            if (dao.hasRealtimeWork()) scheduler.enqueueNextRealtime()
            else if (dao.hasPending()) scheduler.resumeBackfillAfterQuietPeriod()
        }
        return Result.success()
    }

    private fun createForegroundInfo(notificationId: Int): ForegroundInfo {
        val notification = notificationManager.createBankingProgressNotification()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun notificationIdFor(extractionId: Long): Int =
        NOTIFICATION_ID_BASE - (extractionId % NOTIFICATION_ID_RANGE).toInt()

    companion object {
        const val INPUT_MODE = "mode"
        private const val NOTIFICATION_ID_BASE = -2_000_000
        private const val NOTIFICATION_ID_RANGE = 1_000_000_000L
    }
}
