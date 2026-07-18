package com.summer.notifai.banking

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.summer.core.banking.BankAccountOrganizer
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class BankAccountOrganizationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val organizer: BankAccountOrganizer,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = runCatching {
        if (organizer.organizePending(CHUNK_SIZE) == CHUNK_SIZE) Result.retry() else Result.success()
    }.getOrElse { Result.retry() }

    companion object {
        private const val CHUNK_SIZE = 100
    }
}
