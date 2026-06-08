package com.summer.notifai.nerbenchmark

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

class NerBenchmarkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NerBenchmarkWorker.ACTION_RUN) return
        val mode = BenchmarkMode.from(intent.getStringExtra(NerBenchmarkWorker.INPUT_MODE))
        WorkManager.getInstance(context).enqueueUniqueWork(
            NerBenchmarkWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<NerBenchmarkWorker>()
                .setInputData(workDataOf(NerBenchmarkWorker.INPUT_MODE to mode.value))
                .build(),
        )
    }
}
