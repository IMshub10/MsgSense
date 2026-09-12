package com.summer.core

import android.app.Application
import android.os.StrictMode
import com.summer.core.android.notification.AppNotificationManager
import com.summer.core.android.permission.manager.IPermissionManager
import javax.inject.Inject

abstract class BaseApp : Application(){

    @Inject
    lateinit var appNotificationManager: AppNotificationManager

    @Inject
    lateinit var permissionManager: IPermissionManager

    override fun onCreate() {
        super.onCreate()
        if (!isMainProcess()) return
        appNotificationManager.createNotificationChannels()
    }

    protected fun isMainProcess(): Boolean =
        Application.getProcessName() == packageName

    fun setUpStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        )
    }
}
