package com.summer.core.android.notification

import android.app.PendingIntent
import com.summer.core.ui.model.SmsImportanceType

interface NotificationIntentProvider {
    fun provideSummaryPendingIntent(): PendingIntent
    fun provideSmsInboxPendingIntent(senderAddressId: Long, smsImportanceType: SmsImportanceType): PendingIntent
    fun provideSmsProcessingPendingIntent(): PendingIntent
}