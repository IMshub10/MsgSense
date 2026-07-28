package com.summer.core.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = BankingTransactionOverrideEntity.TABLE_NAME,
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
data class BankingTransactionOverrideEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "run_id")
    val runId: Long,
    val merchant: String,
    val amount: String,
    val currency: String,
    val direction: String,
    val category: String,
    @ColumnInfo(name = "payment_method")
    val paymentMethod: String,
    @ColumnInfo(name = "review_state")
    val reviewState: String = "CONFIRMED",
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
) {
    companion object {
        const val TABLE_NAME = "banking_transaction_overrides"
    }
}
