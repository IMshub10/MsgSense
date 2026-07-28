package com.summer.core.data.local.model

import androidx.room.ColumnInfo

data class AccountOrganizingCandidate(
    @ColumnInfo(name = "extraction_id") val extractionId: Long,
    @ColumnInfo(name = "sms_date") val smsDate: Long,
    @ColumnInfo(name = "raw_address") val rawAddress: String,
    @ColumnInfo(name = "bank") val bank: String?,
    @ColumnInfo(name = "account") val account: String?,
    @ColumnInfo(name = "card_type") val cardType: String?,
    @ColumnInfo(name = "balance") val balance: String?,
    @ColumnInfo(name = "balance_currency") val balanceCurrency: String?,
)
