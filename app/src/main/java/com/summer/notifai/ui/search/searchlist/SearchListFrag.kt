package com.summer.notifai.ui.search.searchlist

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.os.bundleOf
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.summer.core.domain.model.SearchSectionId
import com.summer.core.ui.BaseFragment
import com.summer.core.ui.model.SmsImportanceType
import com.summer.notifai.R
import com.summer.notifai.databinding.FragSearchListBinding
import com.summer.notifai.ui.common.PagingLoadStateAdapter
import com.summer.notifai.ui.datamodel.GlobalSearchListItem
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class SearchListFrag : BaseFragment<FragSearchListBinding>() {

    override val layoutResId: Int
        get() = R.layout.frag_search_list

    private val viewModel: SearchListViewModel by viewModels()

    private var _searchListPagingAdapter: SearchListPagingAdapter? = null
    private val searchListPagingAdapter
        get() = _searchListPagingAdapter!!

    private val args: SearchListFragArgs by navArgs()

    private val searchQuery: String by lazy { args.query }

    private val searchType: SearchSectionId by lazy {
        args.searchType.toIntOrNull()?.let { SearchSectionId.fromId(it) }
            ?: SearchSectionId.MESSAGES
    }

    private val senderAddressId: Long by lazy {
        args.senderAddressId.toLongOrNull() ?: 0L
    }

    private lateinit var backPressedCallback: OnBackPressedCallback

    private var contactClicked = false

    override fun onFragmentReady(instanceState: Bundle?) {
        viewModel.setSearchType(searchType)
        Log.d("SearchListFrag", "onFragmentReady called with searchType=$searchType, query=$searchQuery")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("SearchListFrag", "onViewCreated called")
        initSearchView()
        setUpAdapter()
        observeViewModel()
        listeners()
        viewModel.initializeSearchInput(senderAddressId, searchQuery, searchType)
        Log.d("SearchListFrag", "onViewCreated complete")
    }

    private fun listeners() {
        mBinding.etFragSearchListSearch.addTextChangedListener {
            viewModel.setSearchFilter(it.toString())
        }
        backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                findNavController().popBackStack()
            }
        }
        mBinding.ivFragSearchListBack.setOnClickListener {
            if (findNavController().currentDestination?.id == R.id.searchListFrag)
                findNavController().popBackStack()
        }
        activity?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner, backPressedCallback)
    }

    private fun observeViewModel() {
        Log.d("SearchListFrag", "Setting up paging data observer")
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                Log.d("SearchListFrag", "Starting to collect paging data")
                viewModel.pagingData.collectLatest { pagingData ->
                    Log.d("SearchListFrag", "Received paging data: ${System.identityHashCode(pagingData)}, submitting to adapter")
                    searchListPagingAdapter.submitData(pagingData)
                    Log.d("SearchListFrag", "Adapter item count after submit: ${searchListPagingAdapter.itemCount}")
                }
            }
        }
        
        // Add load state listener to track loading
        viewLifecycleOwner.lifecycleScope.launch {
            searchListPagingAdapter.loadStateFlow.collectLatest { loadStates ->
                Log.d("SearchListFrag", "Load state: refresh=${loadStates.refresh}, append=${loadStates.append}, prepend=${loadStates.prepend}")
                Log.d("SearchListFrag", "Current adapter item count: ${searchListPagingAdapter.itemCount}")
            }
        }
    }

    private fun setUpAdapter() {
        _searchListPagingAdapter = SearchListPagingAdapter(
            query = args.query,
            itemClickListener = object : SearchListPagingAdapter.GlobalSearchItemClickListener {
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
            }
        )
        mBinding.rvFragSearchListList.adapter =
            searchListPagingAdapter.withLoadStateHeaderAndFooter(
                header = PagingLoadStateAdapter { searchListPagingAdapter.retry() },
                footer = PagingLoadStateAdapter { searchListPagingAdapter.retry() }
            )
        mBinding.rvFragSearchListList.itemAnimator = null
    }

    private fun initSearchView() {
        mBinding.etFragSearchListSearch.setText(searchQuery)
        mBinding.etFragSearchListSearch.post {
            mBinding.etFragSearchListSearch.requestFocus()
            mBinding.etFragSearchListSearch.setSelection(searchQuery.length)
        }
    }

    override fun onDestroyView() {
        _searchListPagingAdapter = null
        super.onDestroyView()
    }
}