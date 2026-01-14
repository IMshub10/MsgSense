package com.summer.core.data.local.model

import androidx.annotation.Keep
import androidx.room.ColumnInfo

/**
 * Lightweight model for status updates only.
 * Used to efficiently update cached message status without fetching full message data.
 */
@Keep
data class SmsStatusUpdate(
    @ColumnInfo(name = "id")
    val id: Long,
    @ColumnInfo(name = "status")
    val status: Int?
)
