package com.summer.core.di

import com.summer.core.android.notification.AppNotificationManager
import com.summer.core.android.permission.manager.IPermissionManager
import com.summer.core.android.sms.processor.SmsInserter
import com.summer.core.domain.usecase.IsSenderBlockedUseCase
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
