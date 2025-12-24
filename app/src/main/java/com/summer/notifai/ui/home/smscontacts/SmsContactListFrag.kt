package com.summer.notifai.ui.home.smscontacts

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.asFlow
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.summer.core.ui.BaseFragment
import com.summer.core.ui.model.SmsImportanceType.Companion.toSmsImportanceType
import com.summer.notifai.R
import com.summer.notifai.databinding.FragSmsContactListBinding
import com.summer.notifai.ui.common.PagingLoadStateAdapter
import com.summer.notifai.ui.home.HomeViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SmsContactListFrag : BaseFragment<FragSmsContactListBinding>() {
    override val layoutResId: Int
        get() = R.layout.frag_sms_contact_list

    private val homeViewModel: HomeViewModel by activityViewModels()

    private var _smsContactListPagingAdapter: SmsContactListPagingAdapter? = null
    private val contactListPagingAdapter
        get() = _smsContactListPagingAdapter!!

    private var lastImportanceFilter: Boolean? = null

    override fun onFragmentReady(instanceState: Bundle?) {
        super.onFragmentReady(instanceState)
        mBinding.viewModel = homeViewModel
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observePagingData()
        listeners()
    }

    private fun listeners() {
        mBinding.fabFragSmsContactListViewContacts.setOnClickListener {
            if (findNavController().currentDestination?.id == R.id.smsContactListFrag)
                findNavController().navigate(R.id.action_smsContactListFrag_to_newContactListFrag)
        }
        mBinding.ivFragContactListSearch.setOnClickListener {
            if (findNavController().currentDestination?.id == R.id.smsContactListFrag)
                findNavController().navigate(R.id.action_home_to_search)
        }
        mBinding.ivFragContactListMore.setOnClickListener {
            if (findNavController().currentDestination?.id == R.id.smsContactListFrag)
                findNavController().navigate(R.id.action_home_to_settings)
        }
    }

    private fun observePagingData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                homeViewModel.isImportant.asFlow().collectLatest { currentImportance ->
                    val isImportanceChanged = lastImportanceFilter != currentImportance
                    lastImportanceFilter = currentImportance

                    if (isImportanceChanged || _smsContactListPagingAdapter == null) {
                        setupAdapter(currentImportance)
                    }

                    homeViewModel.pagedContacts.collectLatest { pagingData ->
                        contactListPagingAdapter.submitData(pagingData)
                    }
                }
            }
        }
    }

    private fun setupAdapter(currentImportance: Boolean) {
        _smsContactListPagingAdapter = SmsContactListPagingAdapter { model ->
            // Navigate to inbox using Navigation component with SafeArgs bundle
            val bundle = bundleOf(
                "senderAddressId" to model.senderAddressId,
                "smsImportanceType" to currentImportance.toSmsImportanceType().value
            )
            findNavController().navigate(R.id.action_home_to_inbox, bundle)
        }

        mBinding.rvFragContactList.adapter = contactListPagingAdapter.withLoadStateHeaderAndFooter(
            header = PagingLoadStateAdapter { contactListPagingAdapter.retry() },
            footer = PagingLoadStateAdapter { contactListPagingAdapter.retry() }
        )

        mBinding.rvFragContactList.itemAnimator?.apply {
            addDuration = 120
            removeDuration = 120
            changeDuration = 100
            moveDuration = 100
        }

        // Optional scroll-to-top when filter changes
        mBinding.rvFragContactList.scrollToPosition(0)
    }

    override fun onDestroyView() {
        _smsContactListPagingAdapter = null
        super.onDestroyView()
    }
}