package com.summer.core.data.local.entities

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.summer.core.data.local.entities.SmsClassificationTypeEntity.Companion.TABLE_NAME

@Keep
@Entity(tableName = TABLE_NAME)
data class  SmsClassificationTypeEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int,

    @ColumnInfo(name = "multi_label_sms_type")
    val multiLabelSmsType: String?,

    @ColumnInfo(name = "sms_type")
    val smsType: String?,

    @ColumnInfo(name = "aggregate_sms_type")
    val aggregateSmsType: String?,

    @ColumnInfo(name = "is_important")
    val isImportant: Boolean,

    @ColumnInfo(name = "compact_sms_type")
    val compactSmsType: String?,
    
    @ColumnInfo(name = "description", defaultValue = "")
    val description: String = ""
) {
    companion object {
        const val TABLE_NAME = "sms_classification_types"
    }
}