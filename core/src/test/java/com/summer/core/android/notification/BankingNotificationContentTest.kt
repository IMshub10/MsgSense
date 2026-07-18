package com.summer.core.android.notification

import com.summer.core.data.local.entities.SmsNerEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class BankingNotificationContentTest {
    @Test
    fun showsFormattedAmountAndMaskedAccount() {
        assertEquals(
            "1,234.5 • Account ••••5678",
            BankingNotificationContent.from(
                listOf(entity("AMOUNT", "1234.5"), entity("ACCOUNT", "0012345678"))
            ),
        )
    }

    @Test
    fun omitsUnreliableAccountAndFallsBackWithoutEntities() {
        assertEquals(
            "Banking transaction details saved",
            BankingNotificationContent.from(listOf(entity("ACCOUNT", "123"))),
        )
        assertEquals("Banking transaction details saved", BankingNotificationContent.from(emptyList()))
    }

    @Test
    fun supportsAmountOnlyAndAccountOnlyResults() {
        assertEquals("42", BankingNotificationContent.from(listOf(entity("AMOUNT", "42"))))
        assertEquals(
            "Account ••••9876",
            BankingNotificationContent.from(listOf(entity("ACCOUNT", "XXXX9876"))),
        )
    }

    private fun entity(type: String, value: String) = SmsNerEntity(
        extractionId = 1,
        entityOrder = 0,
        entityType = type,
        rawText = value,
        normalizedValue = value,
        startOffset = 0,
        endOffset = value.length,
    )
}
