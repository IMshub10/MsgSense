package com.summer.notifai

import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.airbnb.lottie.BuildConfig
import com.summer.core.BaseApp
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class App : BaseApp(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) setUpStrictMode()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}

