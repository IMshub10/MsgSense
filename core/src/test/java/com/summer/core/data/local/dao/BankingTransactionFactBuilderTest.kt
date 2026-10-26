package com.summer.core.data.local.dao

import com.summer.core.data.local.entities.NerMentionEntity
import org.junit.Assert.*
import org.junit.Test

class BankingTransactionFactBuilderTest {
    @Test
    fun invalidNormalizedAmountsRequireReviewAndAreNotProjectedAsMoney() {
        listOf(null, "", "not a number", "£100.00").forEach { value ->
            val fact = build(value)
            assertEquals("NEEDS_REVIEW", fact.reviewState)
            assertTrue(fact.ambiguityFlags.orEmpty().contains("INVALID_AMOUNT"))
            assertNull(fact.amount)
        }
    }

    @Test
    fun validAmountRetainsCurrencyAndExtractionState() {
        val fact = build("100.25")
        assertEquals("100.25", fact.amount)
        assertEquals("GBP", fact.amountCurrency)
        assertEquals("AI_EXTRACTED", fact.reviewState)
        assertNull(fact.ambiguityFlags)
    }

    private fun build(normalized: String?) = BankingTransactionFactBuilder.build(
        runId = 1,
        mentions = listOf(
            NerMentionEntity(1, 1, 0, "AMOUNT", "£100.25", normalized, 0, 7),
            NerMentionEntity(2, 1, 1, "DIRECTION", "debited", "DEBIT", 8, 15),
        ),
        truncated = false,
        now = 1,
    )
}
