package com.summer.core.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = SmsTransactionAccountLinkEntity.TABLE_NAME,
    foreignKeys = [
        ForeignKey(
            entity = NerRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["extraction_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = BankAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["extraction_id"], unique = true),
        Index(value = ["account_id"]),
    ],
)
data class SmsTransactionAccountLinkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "extraction_id")
    val extractionId: Long,
    @ColumnInfo(name = "account_id")
    val accountId: Long,
    val source: String,
    val confidence: Double,
    val reason: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
) {
    companion object {
        const val TABLE_NAME = "sms_transaction_account_links"
    }
}
