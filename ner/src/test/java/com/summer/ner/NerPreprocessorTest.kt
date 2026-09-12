package com.summer.ner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NerPreprocessorTest {
    @Test
    fun reproducesV50SenderPrefixAndBankingTokenization() {
        val input = NerPreprocessor.preprocess(
            sender = "HDFCBK-S",
            body = "Txn Rs.50.00 at q503@ybl",
        )

        assertEquals(
            listOf(
                "SenderAddressId", ":", "HDFCBK", "Body", ":",
                "Txn", "Rs.", "50.00", "at", "q503@ybl",
            ),
            input.tokens.map { it.text },
        )
        input.tokens.take(input.prefixTokenCount).forEach {
            assertNull(it.bodyStart)
            assertNull(it.bodyEnd)
        }
    }

    @Test
    fun recordsBodyOffsetsAfterRecursiveSplits() {
        val tokens = NerPreprocessor.tokenize("Spent Rs.396 on 2026-01-24.")

        assertEquals(
            listOf("Spent", "Rs.", "396", "on", "2026", "-", "01", "-", "24."),
            tokens.map { it.text },
        )
        assertEquals("2026", "Spent Rs.396 on 2026-01-24.".substring(tokens[4].bodyStart!!, tokens[4].bodyEnd!!))
        assertEquals("24.", "Spent Rs.396 on 2026-01-24.".substring(tokens.last().bodyStart!!, tokens.last().bodyEnd!!))
    }
}
