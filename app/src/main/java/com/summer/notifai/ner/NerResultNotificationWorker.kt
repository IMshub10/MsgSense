package com.summer.notifai.ner

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.summer.core.android.notification.AppNotificationManager
import com.summer.core.data.local.dao.NerDao
import com.summer.core.ner.NerConstants
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

@HiltWorker
class NerResultNotificationWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val dao: NerDao,
    private val notificationManager: AppNotificationManager,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val extractionId = inputData.getLong(INPUT_EXTRACTION_ID, 0L)
        val coordinatorId = inputData.getString(INPUT_COORDINATOR_ID)?.let(UUID::fromString)
        if (coordinatorId != null && !awaitCoordinatorCleanup(coordinatorId)) return Result.retry()
        if (coordinatorId != null) delay(FOREGROUND_CLEANUP_DELAY_MS)

        val ids = if (extractionId > 0L) listOf(extractionId) else dao.readyNotificationIds()
        for (id in ids) postIfReady(id)
        return Result.success()
    }

    private suspend fun postIfReady(extractionId: Long) {
        val notification = dao.notificationByRunId(extractionId) ?: return
        if (notification.state != NerConstants.NOTIFICATION_STATE_READY) return
        val notificationId = notification.notificationId
        if (dao.isExtractionSenderBlocked(extractionId) == true) {
            notificationManager.cancelBankingNotification(notificationId)
            dao.suppressNotification(
                extractionId, NerConstants.SUPPRESSION_BLOCKED_SENDER, System.currentTimeMillis(),
            )
            return
        }
        if (!notificationManager.canShowBankingNotifications()) {
            notificationManager.cancelBankingNotification(notificationId)
            dao.suppressNotification(
                extractionId, NerConstants.SUPPRESSION_NOTIFICATIONS_DISABLED,
                System.currentTimeMillis(),
            )
            return
        }
        notificationManager.showBankingResultNotification(
            notificationId = notificationId,
            extractionId = extractionId,
            amount = notification.amount,
            account = notification.account,
        )
        dao.markNotificationPosted(extractionId, System.currentTimeMillis())
    }

    private suspend fun awaitCoordinatorCleanup(coordinatorId: UUID): Boolean =
        withTimeoutOrNull(COORDINATOR_WAIT_MS) {
            val workManager = WorkManager.getInstance(appContext)
            while (true) {
                val info = withContext(Dispatchers.IO) {
                    workManager.getWorkInfoById(coordinatorId).get()
                }
                if (info == null || info.state.isFinished) return@withTimeoutOrNull true
                delay(COORDINATOR_POLL_MS)
            }
            @Suppress("UNREACHABLE_CODE")
            false
        } == true

    companion object {
        const val INPUT_EXTRACTION_ID = "extraction_id"
        const val INPUT_COORDINATOR_ID = "coordinator_id"
        private const val COORDINATOR_WAIT_MS = 15_000L
        private const val COORDINATOR_POLL_MS = 250L
        private const val FOREGROUND_CLEANUP_DELAY_MS = 300L
    }
}
