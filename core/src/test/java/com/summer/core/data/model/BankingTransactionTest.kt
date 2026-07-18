package com.summer.core.data.model

import com.summer.core.data.local.model.TransactionProjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BankingTransactionTest {
    @Test
    fun mapsCompleteExtractionWithoutGuessingCategory() {
        val transaction = BankingTransaction.from(
            row(
                rawMerchant = "Local Cafe",
                rawAmount = "INR 450.00",
                normalizedAmount = "450.00",
                normalizedDirection = "debit",
                account = "0012345678",
                txnType = "UPI",
            )
        )

        assertEquals("Local Cafe", transaction.merchant)
        assertEquals("-₹450", transaction.signedAmount)
        assertEquals("Other", transaction.category)
        assertEquals("UPI", transaction.paymentMethod)
        assertEquals("••••5678", transaction.maskedAccount)
        assertEquals("AI_EXTRACTED", transaction.reviewState)
    }

    @Test
    fun marksIncompleteExtractionForReviewAndDoesNotGuessCurrency() {
        val transaction = BankingTransaction.from(
            row(normalizedAmount = null, normalizedDirection = null, rawAmount = "450")
        )

        assertEquals("", transaction.currency)
        assertEquals("Amount unavailable", transaction.signedAmount)
        assertEquals("NEEDS_REVIEW", transaction.reviewState)
        assertNull(BankingTransaction.maskAccount("123"))
    }

    @Test
    fun userOverrideWinsAndMarksTransactionConfirmed() {
        val transaction = BankingTransaction.from(
            row(
                normalizedAmount = "10",
                normalizedDirection = "debit",
                overrideMerchant = "Correct Merchant",
                overrideAmount = "12.5",
                overrideCurrency = "USD",
                overrideDirection = "CREDIT",
                overrideCategory = "Food",
                overridePaymentMethod = "Card",
                overrideReviewState = "CONFIRMED",
            )
        )

        assertEquals("Correct Merchant", transaction.merchant)
        assertEquals("+$12.5", transaction.signedAmount)
        assertEquals("Food", transaction.category)
        assertEquals("Card", transaction.paymentMethod)
        assertEquals("CONFIRMED", transaction.reviewState)
    }

    private fun row(
        rawMerchant: String? = null,
        rawAmount: String? = null,
        normalizedAmount: String? = null,
        normalizedDirection: String? = null,
        account: String? = null,
        txnType: String? = null,
        overrideMerchant: String? = null,
        overrideAmount: String? = null,
        overrideCurrency: String? = null,
        overrideDirection: String? = null,
        overrideCategory: String? = null,
        overridePaymentMethod: String? = null,
        overrideReviewState: String? = null,
    ) = TransactionProjection(
        extractionId = 1,
        smsId = 2,
        senderAddressId = 3,
        smsBody = "Synthetic test message",
        smsDate = 1_700_000_000_000,
        rawMerchant = rawMerchant,
        rawAmount = rawAmount,
        normalizedAmount = normalizedAmount,
        rawDirection = normalizedDirection,
        normalizedDirection = normalizedDirection,
        bank = "Test Bank",
        account = account,
        txnType = txnType,
        cardType = null,
        truncated = false,
        overrideMerchant = overrideMerchant,
        overrideAmount = overrideAmount,
        overrideCurrency = overrideCurrency,
        overrideDirection = overrideDirection,
        overrideCategory = overrideCategory,
        overridePaymentMethod = overridePaymentMethod,
        overrideReviewState = overrideReviewState,
    )
}
