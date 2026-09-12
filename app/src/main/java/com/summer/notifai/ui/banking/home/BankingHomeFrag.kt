package com.summer.notifai.ui.banking.home

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.summer.core.data.model.CurrencyTotals
import com.summer.core.data.model.MonthlyCashFlow
import com.summer.core.ui.BaseFragment
import com.summer.notifai.R
import com.summer.notifai.databinding.FragBankingHomeBinding
import com.summer.notifai.ui.banking.list.TransactionAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BankingHomeFrag : BaseFragment<FragBankingHomeBinding>() {
    override val layoutResId = R.layout.frag_banking_home
    private val viewModel: BankingHomeViewModel by viewModels()
    private val accountAdapter = AccountAdapter(::openAccount)
    private val dashboardAccountAdapter = AccountAdapter(::openAccount)
    private val recentAdapter = TransactionAdapter(onClick = {
        findNavController().navigate(
            R.id.action_bankingHome_to_transactionDetail,
            bundleOf("extractionId" to it.extractionId),
        )
    })

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mBinding.accountsList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = accountAdapter
            isNestedScrollingEnabled = false
        }
        mBinding.dashboardAccounts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = dashboardAccountAdapter
            isNestedScrollingEnabled = false
        }
        mBinding.recentList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = recentAdapter
            isNestedScrollingEnabled = false
        }
        mBinding.back.setOnClickListener { findNavController().popBackStack() }
        mBinding.bottomNav.setOnItemSelectedListener {
            showTab(it.itemId)
            true
        }
        mBinding.periods.setOnCheckedStateChangeListener { _, checked ->
            when (checked.firstOrNull()) {
                R.id.period_day -> viewModel.setPeriod(BankingHomeViewModel.Period.DAY)
                R.id.period_year -> viewModel.setPeriod(BankingHomeViewModel.Period.YEAR)
                R.id.period_custom -> showDatePicker()
                else -> viewModel.setPeriod(BankingHomeViewModel.Period.MONTH)
            }
        }
        collectState()
    }

    private fun collectState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.overview.collect { overview ->
                        val visibleAccounts = overview.accounts.filterNot { it.isHidden }
                        accountAdapter.submitList(overview.accounts)
                        recentAdapter.submitList(overview.recentTransactions)
                        dashboardAccountAdapter.submitList(visibleAccounts.take(3))
                        bindTotals(mBinding.dashboardTotals, overview.currentMonthTotals)
                        mBinding.cashFlow.submit(overview.sixMonthCashFlow)
                        mBinding.empty.visibility = if (overview.accounts.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.insights.collect { (totals, categories) ->
                        bindTotals(mBinding.insightTotals, totals)
                        mBinding.insightCashFlow.submit(
                            totals.map { MonthlyCashFlow(0, it.currency, it.debit, it.credit) }
                        )
                        mBinding.categoryDonut.submit(categories)
                        mBinding.categoryTotals.removeAllViews()
                        categories.sortedByDescending { it.amount }.forEach {
                            mBinding.categoryTotals.addView(summaryText("${it.category}: ${it.currency} ${it.amount}"))
                        }
                    }
                }
            }
        }
    }

    private fun bindTotals(container: android.widget.LinearLayout, totals: List<CurrencyTotals>) {
        container.removeAllViews()
        totals.forEach {
            container.addView(
                summaryText(
                    "${it.currency}: Debit ${it.debit}  •  Credit ${it.credit}  •  Net ${it.credit.subtract(it.debit)}  •  Reversal ${it.reversal}" +
                        if (it.excludedCount > 0) "  •  ${it.excludedCount} excluded" else ""
                )
            )
        }
    }

    private fun summaryText(value: String) = TextView(requireContext()).apply {
        text = value
        setTextAppearance(R.style.text_body)
        val padding = (8 * resources.displayMetrics.density).toInt()
        setPadding(0, padding, 0, padding)
    }

    private fun showTab(itemId: Int) = with(mBinding) {
        dashboard.visibility = if (itemId == R.id.banking_dashboard) View.VISIBLE else View.GONE
        accounts.visibility = if (itemId == R.id.banking_accounts) View.VISIBLE else View.GONE
        insights.visibility = if (itemId == R.id.banking_insights) View.VISIBLE else View.GONE
        title.setText(
            when (itemId) {
                R.id.banking_accounts -> R.string.accounts
                R.id.banking_insights -> R.string.insights
                else -> R.string.banking_dashboard
            }
        )
    }

    private fun showDatePicker() {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(R.string.custom_timeframe)
            .build()
        picker.addOnPositiveButtonClickListener { range ->
            viewModel.setCustomRange(range.first, range.second)
        }
        picker.show(parentFragmentManager, "banking_date_range")
    }

    private fun openAccount(account: com.summer.core.data.model.BankAccount) {
        findNavController().navigate(
            R.id.action_bankingHome_to_accountDetail,
            bundleOf("accountId" to account.id),
        )
    }
}
