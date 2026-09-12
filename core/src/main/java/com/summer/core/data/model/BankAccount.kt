package com.summer.core.data.model

data class BankAccount(
    val id: Long,
    val canonicalBank: String,
    val instrumentType: String,
    val name: String,
    val maskedIdentifier: String?,
    val logoKey: String?,
    val isHidden: Boolean,
    val lastReportedBalance: String?,
    val balanceCurrency: String?,
    val balanceObservedAt: Long?,
    val transactions: List<BankingTransaction>,
)
