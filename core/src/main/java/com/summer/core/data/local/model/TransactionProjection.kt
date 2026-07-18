package com.summer.core.data.local.model

import androidx.room.ColumnInfo

data class TransactionProjection(
    @ColumnInfo(name = "extraction_id") val extractionId: Long,
    @ColumnInfo(name = "sms_id") val smsId: Long,
    @ColumnInfo(name = "sender_address_id") val senderAddressId: Long,
    @ColumnInfo(name = "sms_body") val smsBody: String,
    @ColumnInfo(name = "sms_date") val smsDate: Long,
    @ColumnInfo(name = "raw_merchant") val rawMerchant: String?,
    @ColumnInfo(name = "raw_amount") val rawAmount: String?,
    @ColumnInfo(name = "normalized_amount") val normalizedAmount: String?,
    @ColumnInfo(name = "raw_direction") val rawDirection: String?,
    @ColumnInfo(name = "normalized_direction") val normalizedDirection: String?,
    @ColumnInfo(name = "bank") val bank: String?,
    @ColumnInfo(name = "account") val account: String?,
    @ColumnInfo(name = "txn_type") val txnType: String?,
    @ColumnInfo(name = "card_type") val cardType: String?,
    @ColumnInfo(name = "balance") val balance: String? = null,
    @ColumnInfo(name = "raw_balance") val rawBalance: String? = null,
    @ColumnInfo(name = "account_id") val accountId: Long? = null,
    @ColumnInfo(name = "truncated") val truncated: Boolean?,
    @ColumnInfo(name = "override_merchant") val overrideMerchant: String?,
    @ColumnInfo(name = "override_amount") val overrideAmount: String?,
    @ColumnInfo(name = "override_currency") val overrideCurrency: String?,
    @ColumnInfo(name = "override_direction") val overrideDirection: String?,
    @ColumnInfo(name = "override_category") val overrideCategory: String?,
    @ColumnInfo(name = "override_payment_method") val overridePaymentMethod: String?,
    @ColumnInfo(name = "override_review_state") val overrideReviewState: String?,
)
