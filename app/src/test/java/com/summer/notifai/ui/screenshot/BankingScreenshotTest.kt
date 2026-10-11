package com.summer.notifai.ui.screenshot

import android.text.format.DateFormat
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.android.material.chip.Chip
import com.summer.core.data.model.BankingTransaction
import com.summer.notifai.R
import com.summer.notifai.databinding.FragTransactionDetailBinding
import com.summer.notifai.databinding.FragTransactionEditBinding
import com.summer.notifai.databinding.FragTransactionListBinding
import com.summer.notifai.ui.banking.list.TransactionAdapter
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi")
class BankingScreenshotTest {
    @Test fun listEmpty() = captureList(message = R.string.no_transactions)
    @Test fun listPreparing() = captureList(message = R.string.preparing_transaction_analysis)
    @Test fun listAnalyzing() = captureList(message = R.string.analyzing_transactions, progress = true)
    @Test fun listSearchNoResults() = captureList(message = R.string.no_matching_transactions)
    @Test fun listPopulatedMixedStates() = captureList(
        items = listOf(ScreenshotFixtures.expense, ScreenshotFixtures.income, ScreenshotFixtures.missing),
    )
    @Test fun listLongContent() = captureList(items = listOf(ScreenshotFixtures.long))
    @Test fun listReviewFilter() = captureList(
        items = listOf(ScreenshotFixtures.expense, ScreenshotFixtures.missing),
        selectedFilter = R.id.filter_review,
    )
    @Test fun listExpenseFilter() = captureList(
        items = listOf(ScreenshotFixtures.expense),
        selectedFilter = R.id.filter_expense,
    )
    @Test fun listIncomeFilter() = captureList(
        items = listOf(ScreenshotFixtures.income),
        selectedFilter = R.id.filter_income,
    )
    @Test fun detailComplete() = captureDetail(ScreenshotFixtures.expense)
    @Test fun detailMissingData() = captureDetail(ScreenshotFixtures.missing)
    @Test fun detailConfirmed() = captureDetail(ScreenshotFixtures.income)
    @Test fun detailLongContent() = captureDetail(ScreenshotFixtures.long)
    @Test fun editInitial() = captureEdit(ScreenshotFixtures.expense)
    @Test fun editMissingData() = captureEdit(ScreenshotFixtures.missing)
    @Test fun editValidationErrors() = captureEdit(ScreenshotFixtures.expense, errors = true)
    @Test fun editFocusedField() = captureEdit(ScreenshotFixtures.expense, focusMerchant = true)

    @Test
    @Config(qualifiers = "w320dp-h720dp-xxhdpi")
    fun listNarrowPhone() = captureList(items = listOf(ScreenshotFixtures.long, ScreenshotFixtures.expense))

    @Test
    @Config(qualifiers = "w360dp-h780dp-night-xxhdpi")
    fun detailDarkMode() = captureDetail(ScreenshotFixtures.expense)

    @Test fun editLargeFont() = captureEdit(ScreenshotFixtures.expense, fontScale = 1.5f)

    private fun captureList(
        items: List<BankingTransaction> = emptyList(),
        message: Int? = null,
        progress: Boolean = false,
        selectedFilter: Int = R.id.filter_all,
    ) {
        val activity = ScreenshotTestHost.activity()
        val binding = FragTransactionListBinding.bind(
            ScreenshotTestHost.inflate(activity, R.layout.frag_transaction_list)
        )
        val adapter = TransactionAdapter(onClick = {})
        binding.list.layoutManager = LinearLayoutManager(activity)
        binding.list.adapter = adapter
        adapter.submitList(items)
        binding.filters.check(selectedFilter)
        binding.empty.visibility = if (message == null) View.GONE else View.VISIBLE
        message?.let(binding.empty::setText)
        binding.analysisProgress.visibility = if (progress) View.VISIBLE else View.GONE
        binding.root.captureRoboImage()
    }

    private fun captureDetail(item: BankingTransaction) {
        val activity = ScreenshotTestHost.activity()
        val binding = FragTransactionDetailBinding.bind(
            ScreenshotTestHost.inflate(activity, R.layout.frag_transaction_detail)
        )
        binding.merchant.text = item.merchant
        binding.amount.text = item.signedAmount
        binding.reviewState.text = item.reviewState.replace('_', ' ').lowercase()
        binding.category.text = item.category
        binding.payment.text = item.paymentMethod
        binding.timestamp.text = DateFormat.getMediumDateFormat(activity).format(Date(item.timestamp))
        binding.bank.text = listOfNotNull(item.bank, item.maskedAccount).joinToString(" • ")
            .ifBlank { activity.getString(R.string.not_available) }
        binding.originalSms.text = item.originalSms
        binding.root.captureRoboImage()
    }

    private fun captureEdit(
        item: BankingTransaction,
        errors: Boolean = false,
        fontScale: Float = 1f,
        focusMerchant: Boolean = false,
    ) {
        val activity = ScreenshotTestHost.activity(fontScale)
        val binding = FragTransactionEditBinding.bind(
            ScreenshotTestHost.inflate(activity, R.layout.frag_transaction_edit)
        )
        binding.merchant.setText(item.merchant)
        binding.amount.setText(item.amount.orEmpty())
        binding.currency.setText(item.currency)
        binding.originalSms.text = item.originalSms
        addOptions(binding.categories, BankingTransaction.CATEGORIES, item.category)
        addOptions(binding.paymentMethods, BankingTransaction.PAYMENT_METHODS, item.paymentMethod)
        if (item.direction == "CREDIT") binding.income.isChecked = true else binding.expense.isChecked = true
        if (errors) {
            binding.merchantContainer.error = activity.getString(R.string.required)
            binding.amountContainer.error = activity.getString(R.string.enter_positive_amount)
            binding.currencyContainer.error = activity.getString(R.string.enter_currency_code)
        }
        if (focusMerchant) binding.merchant.requestFocus()
        binding.root.captureRoboImage()
    }

    private fun addOptions(
        group: com.google.android.material.chip.ChipGroup,
        options: List<String>,
        selected: String,
    ) {
        options.forEach { value ->
            group.addView(Chip(group.context).apply {
                id = View.generateViewId()
                text = value
                isCheckable = true
                isChecked = value == selected
            })
        }
    }
}
