package com.summer.core.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = NerRunMetadataEntity.TABLE_NAME,
    foreignKeys = [
        ForeignKey(
            entity = NerRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["run_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["run_id"], unique = true)],
)
data class NerRunMetadataEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "run_id")
    val runId: Long,
    @ColumnInfo(name = "model_id")
    val modelId: String,
    @ColumnInfo(name = "model_sha256")
    val modelSha256: String,
    @ColumnInfo(name = "tokenizer_sha256")
    val tokenizerSha256: String,
    @ColumnInfo(name = "preprocessing_version")
    val preprocessingVersion: String,
    @ColumnInfo(name = "label_schema_sha256")
    val labelSchemaSha256: String?,
    @ColumnInfo(name = "decoder_version")
    val decoderVersion: String?,
    @ColumnInfo(name = "normalizer_version")
    val normalizerVersion: String?,
    @ColumnInfo(name = "token_count")
    val tokenCount: Int,
    val truncated: Boolean,
    @ColumnInfo(name = "inference_ms")
    val inferenceMs: Double,
    @ColumnInfo(name = "mention_count")
    val mentionCount: Int,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
) {
    companion object {
        const val TABLE_NAME = "ner_run_metadata"
    }
}
