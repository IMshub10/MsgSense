package com.summer.core.ner

import com.summer.core.data.local.entities.SmsEntity

object NerConstants {
    const val BANKING_TRANSACTION_CLASSIFICATION_ID = 2
    const val PRIORITY_REALTIME = "REALTIME"
    const val PRIORITY_BACKFILL = "BACKFILL"
    const val MAX_ATTEMPTS = 3
    const val BACKFILL_MAX_RUN_MS = 5 * 60 * 1000L
    const val REALTIME_TIMEOUT_MS = 2 * 60 * 1000L
    const val NOTIFICATION_STATE_NONE = "NONE"
    const val NOTIFICATION_STATE_PROGRESS = "PROGRESS"
    const val NOTIFICATION_STATE_READY = "READY"
    const val NOTIFICATION_STATE_POSTED = "POSTED"
    const val NOTIFICATION_STATE_SUPPRESSED = "SUPPRESSED"
    const val SUPPRESSION_BLOCKED_SENDER = "BLOCKED_SENDER"
    const val SUPPRESSION_NOTIFICATIONS_DISABLED = "NOTIFICATIONS_DISABLED"
    const val SUPPRESSION_FOREGROUND_REJECTED = "FOREGROUND_REJECTED"
}

interface NerScheduler {
    suspend fun enqueueRealtime(sms: SmsEntity)
    fun enqueueBackfill()
}
