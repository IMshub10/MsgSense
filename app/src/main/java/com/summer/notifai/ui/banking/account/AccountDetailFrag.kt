package com.summer.notifai.ui.banking.account

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.view.View
import android.widget.EditText
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.summer.core.banking.BalanceRefreshMethod
import com.summer.core.banking.BankRegistry
import com.summer.core.data.model.BankAccount
import com.summer.core.data.model.BankingTransaction
import com.summer.core.ui.BaseFragment
import com.summer.notifai.R
import com.summer.notifai.databinding.FragAccountDetailBinding
import com.summer.notifai.ui.banking.list.TransactionAdapter
import dagger.hilt.android.AndroidEntryPoint
import java.math.BigDecimal
import java.util.Date
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AccountDetailFrag : BaseFragment<FragAccountDetailBinding>() {
    override val layoutResId = R.layout.frag_account_detail
    private val viewModel: AccountDetailViewModel by viewModels()
    private val adapter = TransactionAdapter(::openTransaction, ::showAssignment)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mBinding.transactions.layoutManager = LinearLayoutManager(requireContext())
        mBinding.transactions.adapter = adapter
        mBinding.back.setOnClickListener { findNavController().popBackStack() }
        mBinding.rename.setOnClickListener { showRename() }
        mBinding.merge.setOnClickListener { showMerge() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.account.collect { account -> account?.let(::bind) }
            }
        }
    }

    private fun bind(account: BankAccount) = with(mBinding) {
        name.text = account.name
        identity.text = listOfNotNull(account.instrumentType, account.maskedIdentifier).joinToString(" • ")
        balance.text = account.lastReportedBalance?.let { "${account.balanceCurrency.orEmpty()} $it" }
            ?: getString(R.string.balance_not_reported)
        balanceAge.text = account.balanceObservedAt?.let {
            val prefix = if (System.currentTimeMillis() - it > DateUtils.DAY_IN_MILLIS) {
                getString(R.string.balance_may_be_outdated)
            } else getString(R.string.last_reported_balance)
            "$prefix • ${DateFormat.getMediumDateFormat(requireContext()).format(Date(it))}"
        }.orEmpty()
        adapter.submitList(account.transactions)
        totals.text = account.transactions.groupBy { it.currency.ifBlank { "Unknown" } }
            .map { (currency, transactions) ->
                val debit = transactions.filter { it.direction == "DEBIT" }
                    .mapNotNull { it.amount?.toBigDecimalOrNull() }.fold(BigDecimal.ZERO, BigDecimal::add)
                val credit = transactions.filter { it.direction == "CREDIT" }
                    .mapNotNull { it.amount?.toBigDecimalOrNull() }.fold(BigDecimal.ZERO, BigDecimal::add)
                getString(R.string.account_total_summary, currency, debit, credit)
            }.joinToString("\n")
        hide.setText(if (account.isHidden) R.string.show_account else R.string.hide_account)
        hide.setOnClickListener { viewModel.setHidden(!account.isHidden) }
        val service = BankRegistry.actionableByKey(account.canonicalBank)
        refresh.visibility = if (account.instrumentType == "ACCOUNT" && service?.refreshMethod != BalanceRefreshMethod.NONE) View.VISIBLE else View.GONE
        refresh.setOnClickListener { service?.let(::confirmRefresh) }
    }

    private fun confirmRefresh(service: com.summer.core.banking.BankService) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.refresh_reported_balance)
            .setMessage("${service.displayName}\n\n${service.eligibilityNote}\n\nYou will confirm the request in the system dialer or SMS app. The balance updates only after a new bank SMS arrives.")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.continue_label) { _, _ ->
                val intent = when (service.refreshMethod) {
                    BalanceRefreshMethod.DIAL -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:${service.destination}"))
                    BalanceRefreshMethod.SMS_COMPOSER -> Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${service.destination}"))
                        .putExtra("sms_body", service.smsTemplate)
                    BalanceRefreshMethod.NONE -> null
                }
                intent?.let(::startActivity)
            }.show()
    }

    private fun showRename() {
        val input = EditText(requireContext()).apply { setText(viewModel.account.value?.name) }
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.rename_account)
            .setView(input).setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ -> viewModel.rename(input.text.toString()) }.show()
    }

    private fun showMerge() {
        val current = viewModel.account.value ?: return
        val candidates = viewModel.allAccounts.value.filter {
            it.id != current.id && it.canonicalBank == current.canonicalBank && it.instrumentType == current.instrumentType
        }
        if (candidates.isEmpty()) return
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.merge_account)
            .setItems(candidates.map { it.name }.toTypedArray()) { _, index ->
                viewModel.merge(candidates[index])
                findNavController().popBackStack()
            }.show()
    }

    private fun showAssignment(transaction: BankingTransaction) {
        val accounts = viewModel.allAccounts.value.filterNot { it.isHidden || it.instrumentType == "UNASSIGNED" }
        val labels = accounts.map { it.name }.toMutableList().apply { add(getString(R.string.unassign_transaction)) }
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.assign_transaction)
            .setItems(labels.toTypedArray()) { _, index ->
                if (index == accounts.size) viewModel.unassign(transaction.extractionId)
                else viewModel.assign(transaction.extractionId, accounts[index].id)
            }.show()
    }

    private fun openTransaction(transaction: BankingTransaction) {
        findNavController().navigate(
            R.id.action_accountDetail_to_transactionDetail,
            bundleOf("extractionId" to transaction.extractionId),
        )
    }
}
