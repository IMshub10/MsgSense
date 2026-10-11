package com.summer.core.data.local.model

import androidx.room.ColumnInfo

data class BankingNotificationRow(
    @ColumnInfo(name = "run_id") val runId: Long,
    @ColumnInfo(name = "notification_id") val notificationId: Int,
    @ColumnInfo(name = "state") val state: String,
    @ColumnInfo(name = "amount") val amount: String?,
    @ColumnInfo(name = "account") val account: String?,
)
