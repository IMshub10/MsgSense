package com.summer.core.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = BankingTransactionFactEntity.TABLE_NAME,
    foreignKeys = [
        ForeignKey(
            entity = NerRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["run_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["run_id"], unique = true),
        Index(value = ["review_state"]),
    ],
)
data class BankingTransactionFactEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "run_id")
    val runId: Long,
    val merchant: String?,
    val amount: String?,
    @ColumnInfo(name = "amount_currency")
    val amountCurrency: String?,
    val direction: String?,
    val bank: String?,
    val account: String?,
    @ColumnInfo(name = "card_type")
    val cardType: String?,
    @ColumnInfo(name = "txn_type")
    val txnType: String?,
    val balance: String?,
    @ColumnInfo(name = "balance_currency")
    val balanceCurrency: String?,
    @ColumnInfo(name = "ref_id")
    val refId: String?,
    @ColumnInfo(name = "review_state")
    val reviewState: String,
    @ColumnInfo(name = "ambiguity_flags")
    val ambiguityFlags: String?,
    @ColumnInfo(name = "merchant_entity_id")
    val merchantEntityId: Long?,
    @ColumnInfo(name = "amount_entity_id")
    val amountEntityId: Long?,
    @ColumnInfo(name = "direction_entity_id")
    val directionEntityId: Long?,
    @ColumnInfo(name = "bank_entity_id")
    val bankEntityId: Long?,
    @ColumnInfo(name = "account_entity_id")
    val accountEntityId: Long?,
    @ColumnInfo(name = "card_type_entity_id")
    val cardTypeEntityId: Long?,
    @ColumnInfo(name = "txn_type_entity_id")
    val txnTypeEntityId: Long?,
    @ColumnInfo(name = "balance_entity_id")
    val balanceEntityId: Long?,
    @ColumnInfo(name = "ref_id_entity_id")
    val refIdEntityId: Long?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
) {
    companion object {
        const val TABLE_NAME = "banking_transaction_facts"
    }
}
