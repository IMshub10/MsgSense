package com.summer.core.data.local.entities

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.summer.core.data.local.entities.SenderAddressEntity.Companion.TABLE_NAME

@Keep
@Entity(
    tableName = TABLE_NAME,
    indices = [Index(value = ["sender_address"], unique = true)],
)
data class SenderAddressEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "sender_address")
    val senderAddress: String, // Normalized for matching with contacts

    @ColumnInfo(name = "original_sender_address")
    val originalSenderAddress: String,

    @ColumnInfo(name = "sender_type")
    val senderType: SenderType,

    @ColumnInfo(name = "is_blocked")
    val isBlocked: Boolean = false
) {
    companion object {
        const val TABLE_NAME = "sender_addresses"
    }
}

enum class SenderType {
    BUSINESS, CONTACT
}