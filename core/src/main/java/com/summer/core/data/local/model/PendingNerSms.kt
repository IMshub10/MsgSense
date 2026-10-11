package com.summer.core.data.local.model

import androidx.room.ColumnInfo

data class PendingNerSms(
    @ColumnInfo(name = "extraction_id")
    val extractionId: Long,
    @ColumnInfo(name = "sms_id")
    val smsId: Long,
    @ColumnInfo(name = "sender_address_id")
    val senderAddressId: Long,
    @ColumnInfo(name = "raw_address")
    val rawAddress: String,
    val body: String,
    val priority: String,
    val attempts: Int,
    @ColumnInfo(name = "is_blocked")
    val isBlocked: Boolean,
    @ColumnInfo(name = "notification_id")
    val notificationId: Int?,
)
