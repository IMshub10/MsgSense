package com.summer.core.banking

import com.summer.core.data.local.entities.BankingTransactionFactEntity
import com.summer.core.data.local.entities.NerMentionEntity
import com.summer.core.ner.NerEntityTypes

object BankingTransactionFactBuilder {
    private val supportedDirections = setOf("DEBIT", "CREDIT", "REVERSAL")

    fun build(
        runId: Long,
        mentions: List<NerMentionEntity>,
        truncated: Boolean,
        now: Long,
    ): BankingTransactionFactEntity {
        val byType = mentions.groupBy { it.entityType }
        val merchant = byType.first(NerEntityTypes.MERCHANT)
        val amount = byType.first(NerEntityTypes.AMOUNT)
        val direction = byType.first(NerEntityTypes.DIRECTION)
        val bank = byType.first(NerEntityTypes.BANK)
        val account = byType.first(NerEntityTypes.ACCOUNT)
        val cardType = byType.first(NerEntityTypes.CARD_TYPE)
        val txnType = byType.first(NerEntityTypes.TXN_TYPE)
        val balance = byType.first(NerEntityTypes.BALANCE)
        val refId = byType.first(NerEntityTypes.REF_ID)
        val normalizedDirection = direction?.normalizedValue?.uppercase()

        val ambiguityFlags = buildList {
            if (truncated) add("TRUNCATED")
            if (amount == null) add("MISSING_AMOUNT")
            if (normalizedDirection !in supportedDirections) add("UNSUPPORTED_DIRECTION")
            if (byType.count(NerEntityTypes.AMOUNT) > 1) add("MULTIPLE_AMOUNTS")
            if (byType.count(NerEntityTypes.ACCOUNT) > 1) add("MULTIPLE_ACCOUNTS")
        }

        return BankingTransactionFactEntity(
            runId = runId,
            merchant = merchant?.rawText,
            amount = amount?.normalizedValue,
            amountCurrency = amount?.rawText?.detectCurrency(),
            direction = normalizedDirection,
            bank = bank?.rawText,
            account = account?.normalizedValue,
            cardType = cardType?.normalizedValue,
            txnType = txnType?.normalizedValue,
            balance = balance?.normalizedValue,
            balanceCurrency = balance?.rawText?.detectCurrency(),
            refId = refId?.normalizedValue,
            reviewState = if (ambiguityFlags.isEmpty()) "AI_EXTRACTED" else "NEEDS_REVIEW",
            ambiguityFlags = ambiguityFlags.takeIf { it.isNotEmpty() }?.joinToString(","),
            merchantEntityId = merchant?.id?.takeIf { it > 0 },
            amountEntityId = amount?.id?.takeIf { it > 0 },
            directionEntityId = direction?.id?.takeIf { it > 0 },
            bankEntityId = bank?.id?.takeIf { it > 0 },
            accountEntityId = account?.id?.takeIf { it > 0 },
            cardTypeEntityId = cardType?.id?.takeIf { it > 0 },
            txnTypeEntityId = txnType?.id?.takeIf { it > 0 },
            balanceEntityId = balance?.id?.takeIf { it > 0 },
            refIdEntityId = refId?.id?.takeIf { it > 0 },
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun Map<String, List<NerMentionEntity>>.first(type: String): NerMentionEntity? =
        this[type]?.minByOrNull { it.entityOrder }

    private fun Map<String, List<NerMentionEntity>>.count(type: String): Int =
        this[type]?.size ?: 0

    private fun String.detectCurrency(): String? = when {
        contains("USD", true) || contains("$") -> "USD"
        contains("EUR", true) || contains("€") -> "EUR"
        contains("GBP", true) || contains("£") -> "GBP"
        contains("INR", true) || contains("RS", true) || contains("₹") -> "INR"
        else -> null
    }
}
