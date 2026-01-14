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
import androidx.recyclerview.widget.ConcatAdapter
import com.summer.core.ui.BaseFragment
import androidx.paging.LoadState
import com.summer.core.ui.model.SmsImportanceType.Companion.toSmsImportanceType
import com.summer.notifai.R
import com.summer.notifai.databinding.FragSmsContactListBinding
import com.summer.notifai.ui.datamodel.ContactMessageInfoDataModel
import com.summer.notifai.ui.common.SafeLinearLayoutManager
import com.summer.notifai.ui.home.HomeViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SmsContactListFrag : BaseFragment<FragSmsContactListBinding>() {
    override val layoutResId: Int
        get() = R.layout.frag_sms_contact_list

    private val homeViewModel: HomeViewModel by activityViewModels()

    private var _smsContactListPagingAdapter: SmsContactListPagingAdapter? = null
    private val contactListPagingAdapter
        get() = _smsContactListPagingAdapter!!

    private var _syncProgressAdapter: SyncProgressAdapter? = null

    private val syncProgressAdapter
        get() = _syncProgressAdapter!!

    private var _concatAdapter: ConcatAdapter? = null
    private val concatAdapter
        get() = _concatAdapter!!

    private var currentImportanceForClick: Boolean = true
    private var shouldScrollToTopOnNextUpdate = false

    private var lastImportance: Boolean? = null

    override fun onFragmentReady(instanceState: Bundle?) {
        super.onFragmentReady(instanceState)
        mBinding.viewModel = homeViewModel
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapter()
        observePagingData()
        observeImportanceChanges()
        observeSyncState()
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

    private fun setupAdapter() {
        _smsContactListPagingAdapter = SmsContactListPagingAdapter { model ->
            navigateToInbox(model)
        }
        _syncProgressAdapter = SyncProgressAdapter()
        _concatAdapter = ConcatAdapter(contactListPagingAdapter)

        mBinding.rvFragContactList.layoutManager = SafeLinearLayoutManager(requireContext())
        mBinding.rvFragContactList.adapter = concatAdapter
        mBinding.rvFragContactList.itemAnimator = null

        contactListPagingAdapter.addLoadStateListener { loadState ->
            if (shouldScrollToTopOnNextUpdate && loadState.source.refresh is LoadState.NotLoading) {
                shouldScrollToTopOnNextUpdate = false
                mBinding.rvFragContactList.scrollToPosition(0)
            }
        }
    }

    private fun observePagingData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                homeViewModel.pagedContacts.collectLatest { pagingData ->
                    contactListPagingAdapter.submitData(pagingData)
                }
            }
        }
    }

    private fun observeImportanceChanges() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                homeViewModel.isImportant.asFlow()
                    .distinctUntilChanged()
                    .collect { importance ->
                        currentImportanceForClick = importance

                        // If importance changed, scroll to top on next update
                        if (lastImportance != null && lastImportance != importance) {
                            shouldScrollToTopOnNextUpdate = true
                        }
                        lastImportance = importance
                    }
            }
        }
    }

    private fun observeSyncState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    homeViewModel.isWorkerRunning,
                    homeViewModel.syncProgress
                ) { isRunning, progress ->
                    Pair(isRunning, progress)
                }.collect { (isRunning, progress) ->
                    val adapter = concatAdapter
                    val headerAdapter = syncProgressAdapter

                    val hasHeader = adapter.adapters.contains(headerAdapter)

                    if (isRunning) {
                        if (!hasHeader) adapter.addAdapter(0, headerAdapter)
                        headerAdapter.updateProgress(progress)
                    } else {
                        if (hasHeader) adapter.removeAdapter(headerAdapter)
                    }
                }
            }
        }
    }

    private fun navigateToInbox(model: ContactMessageInfoDataModel) {
        val bundle = bundleOf(
            "senderAddressId" to model.senderAddressId,
            "smsImportanceType" to currentImportanceForClick.toSmsImportanceType().value
        )
        findNavController().navigate(R.id.action_home_to_inbox, bundle)
    }

    override fun onDestroyView() {
        _smsContactListPagingAdapter = null
        _syncProgressAdapter = null
        _concatAdapter = null
        super.onDestroyView()
    }
}