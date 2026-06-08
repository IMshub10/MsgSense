package com.summer.notifai.nerbenchmark

import android.app.Activity
import android.os.Bundle
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

class NerBenchmarkLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = BenchmarkMode.from(intent.getStringExtra(NerBenchmarkWorker.INPUT_MODE))
        WorkManager.getInstance(this).enqueueUniqueWork(
            NerBenchmarkWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<NerBenchmarkWorker>()
                .setInputData(workDataOf(NerBenchmarkWorker.INPUT_MODE to mode.value))
                .build(),
        )
    }
}
