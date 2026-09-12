package com.summer.ner

import org.junit.Assert.assertEquals
import org.junit.Test

class NerNormalizerTest {
    @Test
    fun normalizesCommonBankingValues() {
        assertEquals("1234.5", NerNormalizer.normalize("AMOUNT", "Rs. 1,234.50"))
        assertEquals("DEBIT", NerNormalizer.normalize("DIRECTION", "spent"))
        assertEquals("name@bank", NerNormalizer.normalize("UPI_ID", "Name@BANK"))
    }
}
