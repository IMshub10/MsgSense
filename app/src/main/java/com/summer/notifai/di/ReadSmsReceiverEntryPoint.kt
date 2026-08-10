package com.summer.notifai.di

import com.summer.core.android.notification.AppNotificationManager
import com.summer.core.android.permission.manager.IPermissionManager
import com.summer.notifai.android.processor.SmsInserter
import com.summer.notifai.domain.usecase.IsSenderBlockedUseCase
import com.summer.core.ner.NerScheduler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReadSmsReceiverEntryPoint {
    fun smsInserter(): SmsInserter
    fun appNotificationManager(): AppNotificationManager
    fun permissionManager(): IPermissionManager
    fun isSenderBlockedUseCase(): IsSenderBlockedUseCase
    fun nerScheduler(): NerScheduler
}
