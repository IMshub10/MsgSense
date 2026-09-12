package com.summer.core.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = NerMentionEntity.TABLE_NAME,
    foreignKeys = [
        ForeignKey(
            entity = NerRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["run_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("run_id"),
        Index(value = ["run_id", "entity_type", "entity_order"]),
    ],
)
data class NerMentionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "run_id")
    val runId: Long,
    @ColumnInfo(name = "entity_order")
    val entityOrder: Int,
    @ColumnInfo(name = "entity_type")
    val entityType: String,
    @ColumnInfo(name = "raw_text")
    val rawText: String,
    @ColumnInfo(name = "normalized_value")
    val normalizedValue: String?,
    @ColumnInfo(name = "start_offset")
    val startOffset: Int,
    @ColumnInfo(name = "end_offset")
    val endOffset: Int,
) {
    companion object {
        const val TABLE_NAME = "ner_mentions"
    }
}
