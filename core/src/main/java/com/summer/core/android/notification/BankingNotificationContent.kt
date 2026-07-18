package com.summer.core.android.notification

import com.summer.core.data.local.entities.SmsNerEntity
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

object BankingNotificationContent {
    fun from(entities: List<SmsNerEntity>): String {
        val amount = entities.firstValue("AMOUNT")?.let(::formatAmount)
        val account = entities.firstValue("ACCOUNT")?.let(::lastFourDigits)
        return when {
            amount != null && account != null -> "$amount • Account ••••$account"
            amount != null -> amount
            account != null -> "Account ••••$account"
            else -> "Banking transaction details saved"
        }
    }

    private fun List<SmsNerEntity>.firstValue(type: String): String? =
        firstOrNull { it.entityType == type }?.normalizedValue

    private fun formatAmount(value: String): String =
        runCatching {
            NumberFormat.getNumberInstance(Locale.US).format(BigDecimal(value))
        }.getOrDefault(value)

    private fun lastFourDigits(value: String): String? {
        val digits = value.filter(Char::isDigit)
        return digits.takeIf { it.length >= 4 }?.takeLast(4)
    }
}
