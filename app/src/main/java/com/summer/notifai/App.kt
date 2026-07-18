package com.summer.notifai

import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.summer.core.BaseApp
import com.summer.notifai.ner.NerStartupEntryPoint
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import javax.inject.Inject

@HiltAndroidApp
class App : BaseApp(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        if (!isMainProcess()) return
        if (BuildConfig.DEBUG) setUpStrictMode()
        EntryPointAccessors.fromApplication(this, NerStartupEntryPoint::class.java)
            .scheduler()
            .enqueueBackfill()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
