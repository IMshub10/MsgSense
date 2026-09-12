package com.summer.core.data.model

import com.summer.core.data.local.model.BankingTransactionRow
import java.math.BigDecimal
import java.util.Locale

data class BankingTransaction(
    val extractionId: Long,
    val smsId: Long,
    val senderAddressId: Long,
    val accountId: Long? = null,
    val originalSms: String,
    val timestamp: Long,
    val merchant: String,
    val amount: String?,
    val currency: String,
    val direction: String,
    val category: String,
    val paymentMethod: String,
    val bank: String?,
    val maskedAccount: String?,
    val reviewState: String,
) {
    val isExpense: Boolean get() = direction == "DEBIT"
    val signedAmount: String
        get() {
            val value = amount ?: return "Amount unavailable"
            val prefix = if (isExpense) "-" else if (direction in setOf("CREDIT", "REVERSAL")) "+" else ""
            return "$prefix${currencySymbol(currency)}$value"
        }

    companion object {
        val CATEGORIES = listOf("Food", "Coffee", "Shopping", "Transport", "Retail", "Bills", "Other")
        val PAYMENT_METHODS = listOf("UPI", "Card", "Bank Transfer", "Cash", "Other")
        val DIRECTIONS = listOf("DEBIT", "CREDIT")

        fun from(row: BankingTransactionRow): BankingTransaction {
            val direction = row.overrideDirection ?: row.direction.orEmpty().uppercase(Locale.US)
            val amount = row.overrideAmount ?: row.amount
            val reviewState = row.overrideReviewState ?: row.reviewState ?: if (
                row.truncated == true || amount.isNullOrBlank() ||
                direction !in setOf("DEBIT", "CREDIT", "REVERSAL")
            ) "NEEDS_REVIEW" else "AI_EXTRACTED"
            return BankingTransaction(
                extractionId = row.runId,
                smsId = row.smsId,
                senderAddressId = row.senderAddressId,
                accountId = row.accountId,
                originalSms = row.smsBody,
                timestamp = row.smsDate,
                merchant = row.overrideMerchant ?: row.merchant ?: row.bank ?: "Bank transaction",
                amount = amount?.let(::cleanDecimal),
                currency = row.overrideCurrency ?: row.currency.orEmpty(),
                direction = direction.ifBlank { "UNKNOWN" },
                category = row.overrideCategory ?: "Other",
                paymentMethod = row.overridePaymentMethod ?: inferPaymentMethod(row.txnType, row.cardType),
                bank = row.bank,
                maskedAccount = row.account?.let(::maskAccount),
                reviewState = reviewState,
            )
        }

        fun maskAccount(value: String): String? {
            val digits = value.filter(Char::isDigit)
            return digits.takeIf { it.length >= 4 }?.takeLast(4)?.let { "••••$it" }
        }

        fun detectCurrency(raw: String?): String = when {
            raw == null -> ""
            raw.contains("USD", true) || raw.contains("$") -> "USD"
            raw.contains("EUR", true) || raw.contains("€") -> "EUR"
            raw.contains("GBP", true) || raw.contains("£") -> "GBP"
            raw.contains("INR", true) || raw.contains("RS", true) || raw.contains("₹") -> "INR"
            else -> ""
        }

        private fun inferPaymentMethod(txnType: String?, cardType: String?): String = when {
            txnType?.contains("UPI", true) == true -> "UPI"
            cardType != null -> "Card"
            txnType != null -> "Bank Transfer"
            else -> "Other"
        }

        private fun cleanDecimal(value: String): String =
            runCatching { BigDecimal(value).stripTrailingZeros().toPlainString() }.getOrDefault(value)

        private fun currencySymbol(currency: String): String = when (currency) {
            "INR" -> "₹"
            "USD" -> "$"
            "EUR" -> "€"
            "GBP" -> "£"
            else -> currency.takeIf(String::isNotBlank)?.plus(" ").orEmpty()
        }
    }
}
