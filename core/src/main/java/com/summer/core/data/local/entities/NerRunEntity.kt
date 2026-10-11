package com.summer.core.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = NerRunEntity.TABLE_NAME,
    foreignKeys = [
        ForeignKey(
            entity = SmsEntity::class,
            parentColumns = ["id"],
            childColumns = ["sms_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["sms_id", "pipeline_fingerprint"], unique = true),
        Index(value = ["status", "priority", "created_at"]),
        Index(value = ["active", "status", "completed_at"]),
    ],
)
data class NerRunEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "sms_id")
    val smsId: Long,
    val status: String,
    val priority: String,
    val attempts: Int = 0,
    @ColumnInfo(name = "pipeline_fingerprint")
    val pipelineFingerprint: String,
    @ColumnInfo(name = "active")
    val active: Boolean = true,
    @ColumnInfo(name = "stale_reason")
    val staleReason: String? = null,
    @ColumnInfo(name = "failure_code")
    val failureCode: String? = null,
    @ColumnInfo(name = "failure_message")
    val failureMessage: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "started_at")
    val startedAt: Long? = null,
    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
) {
    companion object {
        const val TABLE_NAME = "ner_runs"
    }
}
