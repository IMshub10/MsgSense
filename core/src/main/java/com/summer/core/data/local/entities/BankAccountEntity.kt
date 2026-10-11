package com.summer.core.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = BankAccountEntity.TABLE_NAME,
    indices = [
        Index(value = ["identifier_fingerprint"], unique = true),
        Index(value = ["canonical_bank", "instrument_type"]),
    ],
)
data class BankAccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "canonical_bank")
    val canonicalBank: String,
    @ColumnInfo(name = "instrument_type")
    val instrumentType: String,
    @ColumnInfo(name = "generated_name")
    val generatedName: String,
    @ColumnInfo(name = "custom_name")
    val customName: String? = null,
    @ColumnInfo(name = "masked_identifier")
    val maskedIdentifier: String?,
    @ColumnInfo(name = "identifier_fingerprint")
    val identifierFingerprint: String,
    @ColumnInfo(name = "logo_key")
    val logoKey: String?,
    @ColumnInfo(name = "is_hidden")
    val isHidden: Boolean = false,
    @ColumnInfo(name = "merge_target_id")
    val mergeTargetId: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
) {
    companion object {
        const val TABLE_NAME = "bank_accounts"
    }
}
