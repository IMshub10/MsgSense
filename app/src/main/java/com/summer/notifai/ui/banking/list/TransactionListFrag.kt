package com.summer.notifai.ui.banking.list

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.summer.core.ui.BaseFragment
import com.summer.notifai.R
import com.summer.notifai.databinding.FragTransactionListBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TransactionListFrag : BaseFragment<FragTransactionListBinding>() {
    override val layoutResId = R.layout.frag_transaction_list
    private val viewModel: TransactionListViewModel by viewModels()
    private val adapter = TransactionAdapter(onClick = { transaction ->
        findNavController().navigate(
            R.id.action_transactionList_to_detail,
            bundleOf("extractionId" to transaction.extractionId),
        )
    })

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mBinding.list.layoutManager = LinearLayoutManager(requireContext())
        mBinding.list.adapter = adapter
        mBinding.back.setOnClickListener { findNavController().popBackStack() }
        mBinding.search.doAfterTextChanged { viewModel.setQuery(it?.toString().orEmpty()) }
        mBinding.filters.setOnCheckedStateChangeListener { _, checkedIds ->
            viewModel.setFilter(
                when (checkedIds.firstOrNull()) {
                    R.id.filter_review -> TransactionListViewModel.Filter.NEEDS_REVIEW
                    R.id.filter_expense -> TransactionListViewModel.Filter.EXPENSE
                    R.id.filter_income -> TransactionListViewModel.Filter.INCOME
                    else -> TransactionListViewModel.Filter.ALL
                }
            )
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submitList(state.transactions)
                    bindEmptyState(state)
                }
            }
        }
    }

    private fun bindEmptyState(state: TransactionListViewModel.UiState) = with(mBinding) {
        val isEmpty = state.transactions.isEmpty()
        empty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        analysisProgress.visibility =
            if (isEmpty && state.summary.activeCount > 0) View.VISIBLE else View.GONE
        empty.setText(
            when {
                !isEmpty -> R.string.no_transactions
                state.filtersApplied -> R.string.no_matching_transactions
                state.summary.activeCount > 0 -> R.string.analyzing_transactions
                state.summary.eligibleCount > state.summary.completedCount ->
                    R.string.preparing_transaction_analysis
                else -> R.string.no_transactions
            }
        )
    }
}
