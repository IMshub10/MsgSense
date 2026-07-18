package com.summer.core.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = SmsNerExtractionEntity.TABLE_NAME,
    foreignKeys = [
        ForeignKey(
            entity = SmsEntity::class,
            parentColumns = ["id"],
            childColumns = ["sms_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["sms_id"], unique = true),
        Index(value = ["status", "priority", "created_at"]),
    ],
)
data class SmsNerExtractionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "sms_id")
    val smsId: Long,
    val status: String,
    val priority: String,
    val attempts: Int = 0,
    @ColumnInfo(name = "model_id")
    val modelId: String? = null,
    @ColumnInfo(name = "model_sha256")
    val modelSha256: String? = null,
    @ColumnInfo(name = "tokenizer_sha256")
    val tokenizerSha256: String? = null,
    @ColumnInfo(name = "preprocessing_version")
    val preprocessingVersion: String? = null,
    @ColumnInfo(name = "token_count")
    val tokenCount: Int? = null,
    val truncated: Boolean? = null,
    @ColumnInfo(name = "inference_ms")
    val inferenceMs: Double? = null,
    @ColumnInfo(name = "entity_count")
    val entityCount: Int? = null,
    @ColumnInfo(name = "notification_ready")
    val notificationReady: Boolean = false,
    @ColumnInfo(name = "notification_id")
    val notificationId: Int? = null,
    @ColumnInfo(name = "notification_state", defaultValue = "'NONE'")
    val notificationState: String = "NONE",
    @ColumnInfo(name = "notification_suppression_reason")
    val notificationSuppressionReason: String? = null,
    @ColumnInfo(name = "result_notification_posted_at")
    val resultNotificationPostedAt: Long? = null,
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
        const val TABLE_NAME = "sms_ner_extractions"
    }
}
