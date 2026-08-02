package com.summer.notifai.ner

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.summer.core.data.local.dao.NerDao
import com.summer.core.data.local.entities.SmsEntity
import com.summer.core.ner.NerConstants
import com.summer.core.ner.NerScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NerWorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: NerDao,
) : NerScheduler {
    override suspend fun enqueueRealtime(sms: SmsEntity) {
        if (sms.smsClassificationTypeId != NerConstants.BANKING_TRANSACTION_CLASSIFICATION_ID) return
        dao.enqueueRealtime(sms.id, NerPipelineMetadata.PIPELINE_FINGERPRINT, System.currentTimeMillis())
        enqueueRealtimeWorker()
    }

    override fun enqueueBackfill() {
        // External trigger (classification success, banking-fragment entry): KEEP dedups into the
        // drain already running — the unique name is the lock.
        enqueueBackfillWorker(delayMs = 0, policy = ExistingWorkPolicy.KEEP)
        enqueueNotificationRecovery()
        enqueueNextRealtime()
    }

    fun enqueueNextRealtime() = enqueueRealtimeWorker()

    fun resumeBackfillAfterQuietPeriod(chainId: String, chainIndex: Int) =
        // Self-continuation baton pass: APPEND_OR_REPLACE (KEEP would drop itself and kill the chain).
        enqueueBackfillWorker(
            delayMs = 0,
            policy = ExistingWorkPolicy.APPEND_OR_REPLACE,
            chainId = chainId,
            chainIndex = chainIndex,
        )

    fun enqueueResultNotification(extractionId: Long, coordinatorId: java.util.UUID) {
        val request = OneTimeWorkRequestBuilder<NerResultNotificationWorker>()
            .setInputData(
                Data.Builder()
                    .putLong(NerResultNotificationWorker.INPUT_EXTRACTION_ID, extractionId)
                    .putString(NerResultNotificationWorker.INPUT_COORDINATOR_ID, coordinatorId.toString())
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "$RESULT_WORK_PREFIX$extractionId",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun enqueueNotificationRecovery() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            RESULT_RECOVERY_WORK,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<NerResultNotificationWorker>().build(),
        )
    }

    private fun enqueueRealtimeWorker() {
        val request = OneTimeWorkRequestBuilder<NerCoordinatorWorker>()
            .setInputData(Data.Builder().putString(NerCoordinatorWorker.INPUT_MODE, MODE_REALTIME).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            REALTIME_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    private fun enqueueBackfillWorker(
        delayMs: Long,
        policy: ExistingWorkPolicy,
        chainId: String? = null,
        chainIndex: Int = 0,
    ) {
        val inputData = Data.Builder()
            .putString(NerCoordinatorWorker.INPUT_MODE, MODE_BACKFILL)
        // Only self-continuations carry a chain id; external triggers start a fresh chain so the
        // worker mints its own id (one drain session = rows sharing that chain id).
        if (chainId != null) {
            inputData.putString(NerCoordinatorWorker.INPUT_CHAIN_ID, chainId)
            inputData.putInt(NerCoordinatorWorker.INPUT_CHAIN_INDEX, chainIndex)
        }
        val request = OneTimeWorkRequestBuilder<NerCoordinatorWorker>()
            .setInputData(inputData.build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            BACKFILL_WORK_NAME,
            policy,
            request,
        )
    }

    companion object {
        const val MODE_REALTIME = "realtime"
        const val MODE_BACKFILL = "backfill"
        private const val REALTIME_WORK_NAME = "production_mobilebert_ner_realtime"
        private const val BACKFILL_WORK_NAME = "production_mobilebert_ner_backfill"
        private const val RESULT_RECOVERY_WORK = "production_mobilebert_ner_notification_recovery"
        private const val RESULT_WORK_PREFIX = "production_mobilebert_ner_notification_"
    }
}
