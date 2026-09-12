package com.summer.notifai.ui.banking.edit

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.summer.core.data.model.BankingTransaction
import com.summer.core.ui.BaseFragment
import com.summer.notifai.R
import com.summer.notifai.databinding.FragTransactionEditBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.math.BigDecimal

@AndroidEntryPoint
class TransactionEditFrag : BaseFragment<FragTransactionEditBinding>() {
    override val layoutResId = R.layout.frag_transaction_edit
    private val viewModel: TransactionEditViewModel by viewModels()
    private var initialized = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        addOptions(mBinding.categories, BankingTransaction.CATEGORIES)
        addOptions(mBinding.paymentMethods, BankingTransaction.PAYMENT_METHODS)
        mBinding.back.setOnClickListener { findNavController().popBackStack() }
        mBinding.save.setOnClickListener { save() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.transaction.collect { transaction ->
                    if (!initialized && transaction != null) bind(transaction)
                }
            }
        }
    }

    private fun addOptions(group: com.google.android.material.chip.ChipGroup, options: List<String>) {
        options.forEach { value ->
            group.addView(Chip(requireContext()).apply {
                id = View.generateViewId()
                text = value
                isCheckable = true
            })
        }
    }

    private fun bind(item: BankingTransaction) = with(mBinding) {
        initialized = true
        merchant.setText(item.merchant)
        amount.setText(item.amount.orEmpty())
        currency.setText(item.currency)
        originalSms.text = item.originalSms
        if (item.direction in setOf("CREDIT", "REVERSAL")) income.isChecked = true else expense.isChecked = true
        checkChip(categories, item.category)
        checkChip(paymentMethods, item.paymentMethod)
    }

    private fun checkChip(group: com.google.android.material.chip.ChipGroup, value: String) {
        (0 until group.childCount).map { group.getChildAt(it) as Chip }
            .firstOrNull { it.text.toString() == value }?.isChecked = true
    }

    private fun selected(group: com.google.android.material.chip.ChipGroup): String? =
        group.findViewById<Chip>(group.checkedChipId)?.text?.toString()

    private fun save() = with(mBinding) {
        merchantContainer.error = null
        amountContainer.error = null
        currencyContainer.error = null
        val merchantValue = merchant.text?.toString()?.trim().orEmpty()
        val amountValue = amount.text?.toString()?.trim().orEmpty()
        val currencyValue = currency.text?.toString()?.trim().orEmpty()
        val amountValid = runCatching { BigDecimal(amountValue) > BigDecimal.ZERO }.getOrDefault(false)
        if (merchantValue.isBlank()) merchantContainer.error = getString(R.string.required)
        if (!amountValid) amountContainer.error = getString(R.string.enter_positive_amount)
        if (currencyValue.length != 3) currencyContainer.error = getString(R.string.enter_currency_code)
        if (merchantValue.isBlank() || !amountValid || currencyValue.length != 3) return
        viewModel.save(
            merchantValue,
            BigDecimal(amountValue).stripTrailingZeros().toPlainString(),
            currencyValue,
            if (income.isChecked) "CREDIT" else "DEBIT",
            selected(categories) ?: "Other",
            selected(paymentMethods) ?: "Other",
        ) { findNavController().popBackStack() }
    }
}
