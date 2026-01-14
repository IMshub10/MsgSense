package com.summer.notifai.ui.search.globalsearch

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.summer.core.ui.BaseFragment
import com.summer.core.ui.model.SmsImportanceType
import com.summer.notifai.R
import com.summer.notifai.databinding.FragGlobalSearchBinding
import com.summer.notifai.ui.datamodel.GlobalSearchListItem
import com.summer.notifai.ui.search.globalsearch.GlobalSearchAdapter.GlobalSearchItemClickListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class GlobalSearchFrag : BaseFragment<FragGlobalSearchBinding>() {
    override val layoutResId: Int
        get() = R.layout.frag_global_search

    private val viewModel: GlobalSearchViewModel by viewModels()

    private var _searchAdapter: GlobalSearchAdapter? = null
    private val searchAdapter get() = _searchAdapter!!

    private var _itemClickListener: GlobalSearchItemClickListener? = null
    private val itemClickListener get() = _itemClickListener!!

    private var contactClicked = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSearchAdapter()
        observeSearchResults()
        initSearchView()
        listeners()
    }

    private fun setupSearchAdapter() {
        _itemClickListener = object : GlobalSearchItemClickListener {
            override fun onSmsClicked(item: GlobalSearchListItem.SmsItem) {
                // Navigate to inbox using Navigation component
                val bundle = bundleOf(
                    "senderAddressId" to item.data.senderAddressId,
                    "smsImportanceType" to SmsImportanceType.ALL.value,
                    "targetSmsId" to item.data.id
                )
                findNavController().navigate(R.id.smsInboxFragment, bundle)
            }

            override fun onConversationClicked(item: GlobalSearchListItem.ConversationItem) {
                // Navigate to inbox using Navigation component
                val bundle = bundleOf(
                    "senderAddressId" to item.data.senderAddressId,
                    "smsImportanceType" to SmsImportanceType.ALL.value
                )
                findNavController().navigate(R.id.smsInboxFragment, bundle)
            }

            override fun onContactClicked(item: GlobalSearchListItem.ContactItem) {
                contactClicked = true
                lifecycleScope.launch(Dispatchers.Default) {
                    val id = viewModel.getOrInsertSenderId(item.data)
                    withContext(Dispatchers.Main) {
                        // Navigate to inbox using Navigation component
                        val bundle = bundleOf(
                            "senderAddressId" to id,
                            "smsImportanceType" to SmsImportanceType.IMPORTANT.value
                        )
                        findNavController().navigate(R.id.smsInboxFragment, bundle)
                        contactClicked = false
                    }
                }
            }

            override fun onHeaderClicked(item: GlobalSearchListItem.SectionHeader) {
                val action =
                    GlobalSearchFragDirections.actionGlobalSearchFragToSearchListFrag(
                        query = viewModel.searchFilter.value.orEmpty(),
                        searchType = item.id.id.toString(),
                        senderAddressId = "0"
                    )
                findNavController().navigate(action)
            }
        }
        _searchAdapter = GlobalSearchAdapter(itemClickListener)
        mBinding.rvFragGlobalSearchList.adapter = searchAdapter
        mBinding.rvFragGlobalSearchList.itemAnimator?.apply {
            addDuration = 120
            removeDuration = 120
            changeDuration = 100
            moveDuration = 100
        }
    }

    private fun observeSearchResults() {
        viewModel.searchResults.observe(viewLifecycleOwner) { items ->
            searchAdapter.updateQuery(viewModel.searchFilter.value.orEmpty())
            searchAdapter.submitList(items)
        }
    }

    private fun listeners() {
        mBinding.ivFragGlobalSearchBack.setOnClickListener {
            if (findNavController().currentDestination?.id == R.id.globalSearchFrag)
                findNavController().popBackStack()
        }
        mBinding.etFragGlobalSearchSearch.addTextChangedListener {
            viewModel.searchFilter.value = it.toString()
        }
    }

    private fun initSearchView() {
        mBinding.etFragGlobalSearchSearch.post {
            mBinding.etFragGlobalSearchSearch.requestFocus()
        }
    }

    override fun onDestroyView() {
        _searchAdapter = null
        _itemClickListener = null
        super.onDestroyView()
    }
}