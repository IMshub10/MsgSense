package com.summer.notifai.ui.banking.detail

import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.summer.core.data.model.BankingTransaction
import com.summer.core.ui.BaseFragment
import com.summer.notifai.R
import com.summer.notifai.databinding.FragTransactionDetailBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Date

@AndroidEntryPoint
class TransactionDetailFrag : BaseFragment<FragTransactionDetailBinding>() {
    override val layoutResId = R.layout.frag_transaction_detail
    private val viewModel: TransactionDetailViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mBinding.back.setOnClickListener { findNavController().popBackStack() }
        mBinding.edit.setOnClickListener {
            findNavController().navigate(
                R.id.action_transactionDetail_to_edit,
                bundleOf("extractionId" to viewModel.extractionId),
            )
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.transaction.collect { it?.let(::bind) }
            }
        }
    }

    private fun bind(item: BankingTransaction) = with(mBinding) {
        merchant.text = item.merchant
        amount.text = item.signedAmount
        reviewState.text = when (item.reviewState) {
            "CONFIRMED" -> getString(R.string.confirmed)
            "NEEDS_REVIEW" -> getString(R.string.needs_review)
            else -> getString(R.string.ai_extracted)
        }
        category.text = item.category
        payment.text = item.paymentMethod
        timestamp.text = DateFormat.getMediumDateFormat(requireContext()).format(Date(item.timestamp)) + " " +
            DateFormat.getTimeFormat(requireContext()).format(Date(item.timestamp))
        bank.text = listOfNotNull(item.bank, item.maskedAccount).joinToString(" • ").ifBlank {
            getString(R.string.not_available)
        }
        originalSms.text = item.originalSms
    }
}
