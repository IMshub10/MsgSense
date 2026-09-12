package com.summer.notifai.ui.screenshot

import com.summer.core.data.model.BankingTransaction

internal object ScreenshotFixtures {
    val expense = transaction(
        id = 1, merchant = "Synthetic Coffee House", amount = "425.50", direction = "DEBIT",
        category = "Coffee", paymentMethod = "Card", bank = "Example Bank", account = "••••1234",
        reviewState = "NEEDS_REVIEW",
    )
    val income = transaction(
        id = 2, merchant = "Synthetic Employer", amount = "85000", direction = "CREDIT",
        category = "Other", paymentMethod = "Bank Transfer", bank = "Local Test Bank",
        account = "••••9876", reviewState = "CONFIRMED",
    )
    val missing = transaction(
        id = 3, merchant = "Bank transaction", amount = null, direction = "UNKNOWN",
        category = "Other", paymentMethod = "Other", bank = null, account = null,
        reviewState = "NEEDS_REVIEW",
    )
    val long = transaction(
        id = 4,
        merchant = "A deliberately very long synthetic merchant name used only for local visual testing",
        amount = "1234567.89", direction = "DEBIT", category = "Shopping", paymentMethod = "UPI",
        bank = "Synthetic International Cooperative Banking Corporation", account = "••••0001",
        reviewState = "AI_EXTRACTED",
        sms = "Synthetic local-only fixture with deliberately long content. No real sender, account, " +
            "message, transaction, or device data is used by screenshot tests.",
    )

    private fun transaction(
        id: Long,
        merchant: String,
        amount: String?,
        direction: String,
        category: String,
        paymentMethod: String,
        bank: String?,
        account: String?,
        reviewState: String,
        sms: String = "Synthetic local-only transaction fixture.",
    ) = BankingTransaction(
        extractionId = id,
        smsId = id,
        senderAddressId = id,
        originalSms = sms,
        timestamp = 1_781_002_800_000L,
        merchant = merchant,
        amount = amount,
        currency = "INR",
        direction = direction,
        category = category,
        paymentMethod = paymentMethod,
        bank = bank,
        maskedAccount = account,
        reviewState = reviewState,
    )
}
