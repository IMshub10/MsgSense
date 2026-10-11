package com.summer.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BankRegistryTest {
    @Test
    fun registryIsValidAndFailsClosed() {
        assertEquals(emptyList<String>(), BankRegistry.validate())
        assertTrue(BankRegistry.entries.count { it.refreshMethod != BalanceRefreshMethod.NONE } >= 10)
        assertTrue(BankRegistry.entries.count { BankRegistry.actionableByKey(it.key) != null } >= 10)
        assertTrue(
            BankRegistry.entries.filter { it.refreshMethod == BalanceRefreshMethod.NONE }
                .all { it.destination == null && it.smsTemplate == null }
        )
    }

    @Test
    fun resolvesCommonAliases() {
        assertEquals("sbi", BankRegistry.resolve("State Bank of India")?.key)
        assertEquals("hdfc", BankRegistry.resolve("HDFC BANK")?.key)
        assertEquals("unknown", BankRegistry.resolve("Example Bank")?.key ?: "unknown")
        assertEquals("sbi", BankRegistry.resolveSender("VM-SBIINB")?.key)
        assertEquals(null, BankRegistry.resolve("Bank"))
    }
}
