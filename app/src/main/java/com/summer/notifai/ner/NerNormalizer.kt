package com.summer.notifai.ner

import java.math.BigDecimal

object NerNormalizer {
    private val whitespace = Regex("\\s+")
    private val currency = Regex("^(INR|RS\\.?|₹|USD|EUR|GBP|\\$|€)\\s*", RegexOption.IGNORE_CASE)

    fun normalize(type: String, raw: String): String? = runCatching {
        val trimmed = raw.trim()
        when (type) {
            "AMOUNT", "BALANCE", "LIMIT" -> {
                val number = currency.replace(trimmed, "").replace(",", "").trim()
                BigDecimal(number).stripTrailingZeros().toPlainString()
            }
            "DIRECTION" -> when {
                trimmed.contains(Regex("debit|spent|paid|withdraw|sent", RegexOption.IGNORE_CASE)) -> "DEBIT"
                trimmed.contains(Regex("credit|received|deposit", RegexOption.IGNORE_CASE)) -> "CREDIT"
                trimmed.contains(Regex("revers|refund", RegexOption.IGNORE_CASE)) -> "REVERSAL"
                trimmed.contains(Regex("declin|failed", RegexOption.IGNORE_CASE)) -> "DECLINED"
                else -> trimmed.uppercase()
            }
            "UPI_ID" -> trimmed.lowercase()
            "ACCOUNT", "REF_ID" -> trimmed.replace(whitespace, "")
            else -> trimmed.replace(whitespace, " ")
        }
    }.getOrNull()
}
