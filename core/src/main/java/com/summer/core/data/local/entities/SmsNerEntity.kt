package com.summer.core.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = SmsNerEntity.TABLE_NAME,
    foreignKeys = [
        ForeignKey(
            entity = SmsNerExtractionEntity::class,
            parentColumns = ["id"],
            childColumns = ["extraction_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("extraction_id"), Index("entity_type")],
)
data class SmsNerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "extraction_id")
    val extractionId: Long,
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
        const val TABLE_NAME = "sms_ner_entities"
    }
}
