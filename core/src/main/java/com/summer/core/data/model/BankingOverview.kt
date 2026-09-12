package com.summer.core.data.model

import java.math.BigDecimal

data class CurrencyTotals(
    val currency: String,
    val debit: BigDecimal,
    val credit: BigDecimal,
    val reversal: BigDecimal,
    val excludedCount: Int,
)

data class MonthlyCashFlow(
    val monthStart: Long,
    val currency: String,
    val debit: BigDecimal,
    val credit: BigDecimal,
)

data class CategoryTotal(
    val category: String,
    val currency: String,
    val amount: BigDecimal,
)

data class BankingOverview(
    val accounts: List<BankAccount> = emptyList(),
    val recentTransactions: List<BankingTransaction> = emptyList(),
    val currentMonthTotals: List<CurrencyTotals> = emptyList(),
    val sixMonthCashFlow: List<MonthlyCashFlow> = emptyList(),
)
