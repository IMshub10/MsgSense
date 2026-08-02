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
import com.summer.core.data.local.entities.NerMentionEntity
import com.summer.core.data.local.model.PendingNerSms
import com.summer.core.ner.NerConstants
import com.summer.core.banking.BankAccountOrganizer
import com.summer.core.worker.WorkerExecution
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID

@HiltWorker
class NerCoordinatorWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val dao: NerDao,
    private val scheduler: NerWorkScheduler,
    private val notificationManager: AppNotificationManager,
    private val accountOrganizer: BankAccountOrganizer,
) : CoroutineWorker(appContext, workerParameters) {

    private data class RunOutcome(
        val result: Result,
        val processed: Int,
        val exitCause: String,
        val failureCode: String? = null,
        val failureMessage: String? = null,
    )

    override suspend fun doWork(): Result {
        val mode = inputData.getString(INPUT_MODE) ?: NerWorkScheduler.MODE_BACKFILL
        val isRealtime = mode == NerWorkScheduler.MODE_REALTIME
        val chainId = inputData.getString(INPUT_CHAIN_ID) ?: UUID.randomUUID().toString()
        val chainIndex = inputData.getInt(INPUT_CHAIN_INDEX, 0)
        dao.recoverInterrupted(
            if (isRealtime) NerConstants.PRIORITY_REALTIME else NerConstants.PRIORITY_BACKFILL,
            System.currentTimeMillis(),
        )

        WorkerExecution.start(
            workerType = WorkerExecution.TYPE_NER,
            mode = if (isRealtime) WorkerExecution.MODE_REALTIME else WorkerExecution.MODE_BACKFILL,
            workRequestId = id.toString(),
            chainId = chainId,
            chainIndex = chainIndex,
            runAttempt = runAttemptCount,
        )
        var outcome = RunOutcome(Result.success(), 0, WorkerExecution.CAUSE_DRAINED)
        try {
            outcome = if (isRealtime) processRealtime() else processBackfill(chainId, chainIndex)
            return outcome.result
        } catch (cancelled: CancellationException) {
            outcome = outcome.copy(result = Result.retry(), exitCause = WorkerExecution.CAUSE_STOPPED)
            throw cancelled
        } finally {
            withContext(NonCancellable) {
                val wasStopped = isStopped
                WorkerExecution.end(
                    workRequestId = id.toString(),
                    result = resultName(outcome.result),
                    exitCause = if (wasStopped) WorkerExecution.CAUSE_STOPPED else outcome.exitCause,
                    wasStopped = wasStopped,
                    stopReason = if (wasStopped) currentStopReason() else null,
                    itemsProcessed = outcome.processed,
                    failureCode = outcome.failureCode,
                    failureMessage = outcome.failureMessage,
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

    private suspend fun processRealtime(): RunOutcome {
        val pending = dao.nextPendingForPriority(NerConstants.PRIORITY_REALTIME)
            ?: return RunOutcome(Result.success(), 0, WorkerExecution.CAUSE_DRAINED)
        val notificationId = pending.notificationId ?: notificationIdFor(pending.extractionId)
        var notificationVisible = prepareRealtimeNotification(pending, notificationId)
        WorkerExecution.foreground(id.toString(), notificationVisible)
        if (dao.markRunning(pending.extractionId, System.currentTimeMillis()) == 0) {
            if (notificationVisible) notificationManager.cancelBankingNotification(notificationId)
            dao.clearNotificationState(pending.extractionId, System.currentTimeMillis())
            return RunOutcome(Result.success(), 0, WorkerExecution.CAUSE_DRAINED)
        }

        val client = NerServiceClient(appContext)
        return try {
            val result = withTimeout(NerConstants.REALTIME_TIMEOUT_MS) {
                client.extract(pending.smsId, pending.rawAddress, pending.body)
            }
            dao.complete(
                runId = pending.extractionId,
                mentions = result.entities.mapIndexed { index, entity ->
                    NerMentionEntity(
                        runId = pending.extractionId,
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
                pipelineFingerprint = result.pipelineFingerprint,
                labelSchemaSha256 = result.labelSchemaSha256,
                decoderVersion = result.decoderVersion,
                normalizerVersion = result.normalizerVersion,
            )
            accountOrganizer.organizePending()
            if (notificationVisible) {
                scheduler.enqueueResultNotification(pending.extractionId, id)
            }
            scheduler.enqueueNextRealtime()
            RunOutcome(Result.success(), 1, WorkerExecution.CAUSE_COMPLETED)
        } catch (timeout: TimeoutCancellationException) {
            notificationManager.cancelBankingNotification(notificationId)
            dao.clearNotificationState(pending.extractionId, System.currentTimeMillis())
            val attempts = pending.attempts + 1
            if (attempts >= NerConstants.MAX_ATTEMPTS) {
                dao.markTerminalFailure(
                    pending.extractionId, System.currentTimeMillis(), "REALTIME_TIMEOUT", null,
                )
                scheduler.enqueueNextRealtime()
                RunOutcome(Result.success(), 0, WorkerExecution.CAUSE_FAILURE, "REALTIME_TIMEOUT")
            } else {
                dao.markPendingFailure(
                    pending.extractionId, System.currentTimeMillis(), "REALTIME_TIMEOUT", null,
                )
                RunOutcome(Result.retry(), 0, WorkerExecution.CAUSE_FAILURE, "REALTIME_TIMEOUT")
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
                RunOutcome(Result.success(), 0, WorkerExecution.CAUSE_FAILURE, code, error.message?.take(200))
            } else {
                dao.markPendingFailure(
                    pending.extractionId, System.currentTimeMillis(), code, error.message?.take(200),
                )
                RunOutcome(Result.retry(), 0, WorkerExecution.CAUSE_FAILURE, code, error.message?.take(200))
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

    private suspend fun processBackfill(chainId: String, chainIndex: Int): RunOutcome {
        dao.enqueueMissingBackfill(NerPipelineMetadata.PIPELINE_FINGERPRINT, System.currentTimeMillis())
        val ranAsForeground = try {
            setForeground(createBackfillForegroundInfo())
            true
        } catch (_: Exception) {
            false
        }
        WorkerExecution.foreground(id.toString(), ranAsForeground)
        val client = NerServiceClient(appContext)
        val started = System.currentTimeMillis()
        var processed = 0
        var realtimePreempted = false
        var drained = false
        try {
            while (!isStopped && System.currentTimeMillis() - started < NerConstants.BACKFILL_MAX_RUN_MS) {
                if (dao.hasRealtimeWork()) {
                    realtimePreempted = true
                    break
                }
                val pending = dao.nextPendingForPriority(NerConstants.PRIORITY_BACKFILL)
                if (pending == null) {
                    drained = true
                    break
                }
                if (dao.markRunning(pending.extractionId, System.currentTimeMillis()) == 0) continue
                try {
                    val result = client.extract(pending.smsId, pending.rawAddress, pending.body)
                    dao.complete(
                        runId = pending.extractionId,
                        mentions = result.entities.mapIndexed { index, entity ->
                            NerMentionEntity(
                                runId = pending.extractionId,
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
                        notificationState = NerConstants.NOTIFICATION_STATE_NONE,
                        now = System.currentTimeMillis(),
                        pipelineFingerprint = result.pipelineFingerprint,
                        labelSchemaSha256 = result.labelSchemaSha256,
                        decoderVersion = result.decoderVersion,
                        normalizerVersion = result.normalizerVersion,
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
            else if (dao.hasPending()) scheduler.resumeBackfillAfterQuietPeriod(chainId, chainIndex + 1)
        }
        val exitCause = when {
            isStopped -> WorkerExecution.CAUSE_STOPPED
            realtimePreempted -> WorkerExecution.CAUSE_REALTIME_PREEMPT
            drained -> WorkerExecution.CAUSE_DRAINED
            else -> WorkerExecution.CAUSE_DURATION_CAP
        }
        return RunOutcome(Result.success(), processed, exitCause)
    }

    private fun createForegroundInfo(notificationId: Int): ForegroundInfo {
        val notification = notificationManager.createBankingProgressNotification()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    // Backfill drains for minutes at a time, so it uses a long-running DATA_SYNC foreground service
    // (like the SMS classifier) rather than the ~3-minute SHORT_SERVICE the realtime path uses.
    private fun createBackfillForegroundInfo(): ForegroundInfo {
        val notification = notificationManager.createBankingProgressNotification()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(BACKFILL_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(BACKFILL_NOTIFICATION_ID, notification)
        }
    }

    private fun notificationIdFor(extractionId: Long): Int =
        NOTIFICATION_ID_BASE - (extractionId % NOTIFICATION_ID_RANGE).toInt()

    companion object {
        const val INPUT_MODE = "mode"
        const val INPUT_CHAIN_ID = "chain_id"
        const val INPUT_CHAIN_INDEX = "chain_index"
        private const val NOTIFICATION_ID_BASE = -2_000_000
        private const val NOTIFICATION_ID_RANGE = 1_000_000_000L
        private const val BACKFILL_NOTIFICATION_ID = 424_200
    }
}
