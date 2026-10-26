package com.summer.ner

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.summer.core.data.local.dao.NerDao
import com.summer.core.ner.NerConstants
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class NerStartupRecoveryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val dao: NerDao,
    private val scheduler: NerWorkScheduler,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        withContext(Dispatchers.IO) {
            NerStartupRepair(dao, preferences(applicationContext), scheduler::enqueueRecoveryBackfill).run()
        }
        Result.success()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Log.e(TAG, "Banking repair failed code=${error.nerFailureCode()}")
        if (runAttemptCount + 1 < NerConstants.MAX_ATTEMPTS) Result.retry() else Result.failure()
    }

    companion object {
        private const val TAG = "NerStartupRecovery"
        private const val WORK_NAME = "banking_repair_v1"
        private const val PREFERENCES = "ner_startup_recovery"

        private fun preferences(context: Context): SharedPreferences =
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

        fun schedule(context: Context) {
            if (preferences(context).getBoolean(NerStartupRepair.COMPLETED_KEY, false)) return
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<NerStartupRecoveryWorker>()
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                    .build(),
            )
        }
    }
}

/** The marker follows both the DB transaction and durable drain scheduling, so interruption is retryable. */
internal class NerStartupRepair(
    private val dao: NerDao,
    private val preferences: SharedPreferences,
    private val enqueueDrain: suspend () -> Unit,
) {
    suspend fun run() {
        if (preferences.getBoolean(COMPLETED_KEY, false)) return
        val requeued = dao.repairBankingData(NerPipelineMetadata.PIPELINE_FINGERPRINT, System.currentTimeMillis())
        enqueueDrain()
        if (!preferences.edit().putBoolean(COMPLETED_KEY, true).commit()) {
            // A failed commit can still update the in-memory preferences; do not skip a retry.
            preferences.edit().remove(COMPLETED_KEY).apply()
            error("Could not persist banking repair marker")
        }
        Log.i("NerStartupRecovery", "Banking repair completed requeued=$requeued")
    }

    companion object {
        const val COMPLETED_KEY = "banking_repair_v1_completed"
    }
}
