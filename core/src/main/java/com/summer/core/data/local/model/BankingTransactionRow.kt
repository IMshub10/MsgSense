package com.summer.core.data.local.model

import androidx.room.ColumnInfo

data class BankingTransactionRow(
    @ColumnInfo(name = "run_id") val runId: Long,
    @ColumnInfo(name = "sms_id") val smsId: Long,
    @ColumnInfo(name = "sender_address_id") val senderAddressId: Long,
    @ColumnInfo(name = "sms_body") val smsBody: String,
    @ColumnInfo(name = "sms_date") val smsDate: Long,
    @ColumnInfo(name = "truncated") val truncated: Boolean?,
    @ColumnInfo(name = "merchant") val merchant: String?,
    @ColumnInfo(name = "amount") val amount: String?,
    @ColumnInfo(name = "currency") val currency: String?,
    @ColumnInfo(name = "direction") val direction: String?,
    @ColumnInfo(name = "bank") val bank: String?,
    @ColumnInfo(name = "account") val account: String?,
    @ColumnInfo(name = "txn_type") val txnType: String?,
    @ColumnInfo(name = "card_type") val cardType: String?,
    @ColumnInfo(name = "balance") val balance: String? = null,
    @ColumnInfo(name = "balance_currency") val balanceCurrency: String? = null,
    @ColumnInfo(name = "review_state") val reviewState: String? = null,
    @ColumnInfo(name = "account_id") val accountId: Long? = null,
    @ColumnInfo(name = "override_merchant") val overrideMerchant: String?,
    @ColumnInfo(name = "override_amount") val overrideAmount: String?,
    @ColumnInfo(name = "override_currency") val overrideCurrency: String?,
    @ColumnInfo(name = "override_direction") val overrideDirection: String?,
    @ColumnInfo(name = "override_category") val overrideCategory: String?,
    @ColumnInfo(name = "override_payment_method") val overridePaymentMethod: String?,
    @ColumnInfo(name = "override_review_state") val overrideReviewState: String?,
)
