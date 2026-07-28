package com.summer.core.android.notification

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

object BankingNotificationContent {
    fun from(amount: String?, account: String?): String {
        val formattedAmount = amount?.let(::formatAmount)
        val accountSuffix = account?.let(::lastFourDigits)
        return when {
            formattedAmount != null && accountSuffix != null -> "$formattedAmount • Account ••••$accountSuffix"
            formattedAmount != null -> formattedAmount
            accountSuffix != null -> "Account ••••$accountSuffix"
            else -> "Banking transaction details saved"
        }
    }

    private fun formatAmount(value: String): String =
        runCatching {
            NumberFormat.getNumberInstance(Locale.US).format(BigDecimal(value))
        }.getOrDefault(value)

    private fun lastFourDigits(value: String): String? {
        val digits = value.filter(Char::isDigit)
        return digits.takeIf { it.length >= 4 }?.takeLast(4)
    }
}
