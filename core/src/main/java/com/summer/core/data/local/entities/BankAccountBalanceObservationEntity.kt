package com.summer.core.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = BankAccountBalanceObservationEntity.TABLE_NAME,
    foreignKeys = [
        ForeignKey(
            entity = BankAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = NerRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_extraction_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["account_id", "observed_at"]),
        Index(value = ["source_extraction_id"], unique = true),
    ],
)
data class BankAccountBalanceObservationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "account_id")
    val accountId: Long,
    @ColumnInfo(name = "source_extraction_id")
    val sourceExtractionId: Long,
    val balance: String,
    val currency: String,
    @ColumnInfo(name = "observed_at")
    val observedAt: Long,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
) {
    companion object {
        const val TABLE_NAME = "bank_account_balance_observations"
    }
}
