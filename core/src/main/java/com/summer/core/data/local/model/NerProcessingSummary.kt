package com.summer.core.data.local.model

import androidx.room.ColumnInfo

data class NerProcessingSummary(
    @ColumnInfo(name = "eligible_count")
    val eligibleCount: Int,
    @ColumnInfo(name = "pending_count")
    val pendingCount: Int,
    @ColumnInfo(name = "running_count")
    val runningCount: Int,
    @ColumnInfo(name = "completed_count")
    val completedCount: Int,
    @ColumnInfo(name = "failed_count")
    val failedCount: Int,
) {
    val activeCount: Int
        get() = pendingCount + runningCount
}
