package com.summer.core.android.notification

import android.app.NotificationManager

enum class NotificationChannelType(
    val channelId: String,
    val channelName: String,
    val importance: Int,
    val description: String
) {
    CRITICAL(
        "channel_critical",
        "Critical Messages",
        NotificationManager.IMPORTANCE_HIGH,
        "Messages that require immediate attention with sound and popup."
    ),
    IMPORTANT(
        "channel_important",
        "Important Messages",
        NotificationManager.IMPORTANCE_DEFAULT,
        "Messages with sound but no popup."
    ),
    GENERAL(
        "channel_general",
        "General Updates",
        NotificationManager.IMPORTANCE_LOW,
        "Silent messages shown in the notification drawer."
    ),
    MINIMAL(
        "channel_minimal",
        "Minimal Priority",
        NotificationManager.IMPORTANCE_MIN,
        "No notification, hidden from drawer."
    ),
    SUMMARY(
        "channel_summary",
        "Daily Summary",
        NotificationManager.IMPORTANCE_LOW,
        "Silent daily digest of messages."
    ),
    SMS_PROCESSING(
        "sms_processing_channel",
        "SMS Processing",
        NotificationManager.IMPORTANCE_MIN,
        "Service notification for SMS processing status."
    );
}
