package com.summer.core.android.phone.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = ContactEntity.TABLE_NAME,
    indices = [Index(value = ["phone_number"], unique = true)],
)
data class ContactEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "phone_number") val phoneNumber: String, // Normalized for matching
    @ColumnInfo(name = "original_phone_number") val originalPhoneNumber: String,
    @ColumnInfo(name = "updated_at_app") val updatedAtApp: Long,
) {
    companion object {
        const val TABLE_NAME = "contacts"
    }
}