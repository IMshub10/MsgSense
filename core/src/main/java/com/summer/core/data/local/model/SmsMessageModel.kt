package com.summer.core.data.local.model

import androidx.annotation.Keep
import androidx.room.ColumnInfo

@Keep
data class SmsMessageModel(
    @ColumnInfo(name = "id")
    val id: Long,
    @ColumnInfo(name = "android_sms_id")
    val androidSmsId: Long?,
    @ColumnInfo(name = "body")
    val body: String?,
    @ColumnInfo(name = "date")
    val date: Long,
    @ColumnInfo(name = "type")
    val type: Int,
    @ColumnInfo(name = "status")
    val status: Int?,
    @ColumnInfo(name = "sms_message_type")
    val smsMessageType: String?,
    @ColumnInfo(name = "compact_type")
    val compactType: String?,
    @ColumnInfo(name = "importance_score")
    val importanceScore: Int?,
    @ColumnInfo(name = "confidence_score")
    val confidenceScore: Float?
)