package com.summer.core.android.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class BankingNotificationContentTest {
    @Test
    fun showsFormattedAmountAndMaskedAccount() {
        assertEquals(
            "1,234.5 • Account ••••5678",
            BankingNotificationContent.from(amount = "1234.5", account = "0012345678"),
        )
    }

    @Test
    fun omitsUnreliableAccountAndFallsBackWithoutEntities() {
        assertEquals(
            "Banking transaction details saved",
            BankingNotificationContent.from(amount = null, account = "123"),
        )
        assertEquals("Banking transaction details saved", BankingNotificationContent.from(amount = null, account = null))
    }

    @Test
    fun supportsAmountOnlyAndAccountOnlyResults() {
        assertEquals("42", BankingNotificationContent.from(amount = "42", account = null))
        assertEquals(
            "Account ••••9876",
            BankingNotificationContent.from(amount = null, account = "XXXX9876"),
        )
    }
}
