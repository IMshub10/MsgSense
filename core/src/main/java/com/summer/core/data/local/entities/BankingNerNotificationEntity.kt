package com.summer.core.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = BankingNerNotificationEntity.TABLE_NAME,
    foreignKeys = [
        ForeignKey(
            entity = NerRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["run_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["run_id"], unique = true),
        Index(value = ["state"]),
    ],
)
data class BankingNerNotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "run_id")
    val runId: Long,
    @ColumnInfo(name = "notification_id")
    val notificationId: Int,
    val state: String,
    @ColumnInfo(name = "suppression_reason")
    val suppressionReason: String? = null,
    @ColumnInfo(name = "posted_at")
    val postedAt: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
) {
    companion object {
        const val TABLE_NAME = "banking_ner_notifications"
    }
}
