package com.summer.ner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NerNormalizerTest {
    @Test
    fun normalizesCommonBankingValues() {
        assertEquals("1234.5", NerNormalizer.normalize("AMOUNT", "Rs. 1,234.50"))
        assertEquals("DEBIT", NerNormalizer.normalize("DIRECTION", "spent"))
        assertEquals("name@bank", NerNormalizer.normalize("UPI_ID", "Name@BANK"))
    }

    @Test
    fun normalizesEverySupportedCurrencySymbolAndRejectsMalformedAmounts() {
        listOf("Rs. ", "INR ", "₹", "USD ", "$", "EUR ", "€", "GBP ", "£").forEach { currency ->
            assertEquals("1234.5", NerNormalizer.normalize("AMOUNT", "${currency}1,234.50"))
        }
        assertEquals("200", NerNormalizer.normalize("BALANCE", "£200.00"))
        assertNull(NerNormalizer.normalize("AMOUNT", "£invalid"))
    }
}
