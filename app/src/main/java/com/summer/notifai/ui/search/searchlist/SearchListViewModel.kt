package com.summer.notifai.ui.search.searchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.summer.core.android.sms.constants.Constants.CONTACT_LIST_PAGE_SIZE
import com.summer.core.android.sms.constants.Constants.SMS_LIST_PAGE_SIZE
import com.summer.core.domain.model.SearchSectionId
import com.summer.core.domain.repository.ISmsRepository
import com.summer.core.domain.usecase.SearchContactsPagingUseCase
import com.summer.core.domain.usecase.SearchConversationsPagingUseCase
import com.summer.core.domain.usecase.SearchMessagesPagingUseCase
import com.summer.core.util.CountryCodeProvider
import com.summer.notifai.ui.datamodel.GlobalSearchListItem
import com.summer.notifai.ui.datamodel.NewContactDataModel
import com.summer.notifai.ui.datamodel.mapper.ContactInfoMapper.toContactMessageInfoDataModel
import com.summer.notifai.ui.datamodel.mapper.NewContactMapper.toNewContactDataModel
import com.summer.notifai.ui.datamodel.mapper.SearchSmsMessageMapper.toSearchSmsMessageDataModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchListViewModel @Inject constructor(
    private val searchMessagesUseCase: SearchMessagesPagingUseCase,
    private val searchConversationsUseCase: SearchConversationsPagingUseCase,
    private val searchContactsUseCase: SearchContactsPagingUseCase,
    private val countryCodeProvider: CountryCodeProvider,
    private val smsRepository: ISmsRepository
) : ViewModel() {

    private val _searchFilter = MutableStateFlow("")

    private var senderAddressId = 0L

    @OptIn(FlowPreview::class)
    private val selectedQuery: StateFlow<String> = _searchFilter
        .debounce(300)
        .map { it.trim().lowercase() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val _searchType = MutableStateFlow(SearchSectionId.MESSAGES)

    private val _pagingData = MutableStateFlow<PagingData<GlobalSearchListItem>>(PagingData.empty())
    val pagingData: StateFlow<PagingData<GlobalSearchListItem>> = _pagingData.asStateFlow()

    init {
        observeSearch()
    }

    fun initializeSearchInput(senderAddressId: Long, searchFilter: String) {
        this.senderAddressId = senderAddressId
        _searchFilter.value = searchFilter.trim()
    }

    fun setSearchFilter(searchFilter: String) {
        _searchFilter.value = searchFilter.trim()
    }

    fun setSearchType(type: SearchSectionId) {
        _searchType.value = type
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeSearch() {
        viewModelScope.launch {
            selectedQuery
                .combine(_searchType) { query, type -> query.trim() to type }
                .flatMapLatest { (query, type) ->
                    if (query.isBlank()) {
                        // emit empty paging data directly
                        flowOf(PagingData.empty())
                    } else {
                        val pagerFlow: Flow<PagingData<GlobalSearchListItem>> = when (type) {
                            SearchSectionId.MESSAGES -> {
                                Pager(
                                    config = PagingConfig(
                                        pageSize = SMS_LIST_PAGE_SIZE,
                                        enablePlaceholders = false
                                    ),
                                    pagingSourceFactory = {
                                        searchMessagesUseCase(
                                            query.lowercase(),
                                            senderAddressId
                                        )
                                    }
                                ).flow.map {
                                    it.map { item -> GlobalSearchListItem.SmsItem(item.toSearchSmsMessageDataModel()) }
                                }
                            }

                            SearchSectionId.CONVERSATIONS -> {
                                Pager(
                                    config = PagingConfig(
                                        pageSize = CONTACT_LIST_PAGE_SIZE,
                                        enablePlaceholders = false
                                    ),
                                    pagingSourceFactory = { searchConversationsUseCase(query.lowercase()) }
                                ).flow.map {
                                    it.map { item -> GlobalSearchListItem.ConversationItem(item.toContactMessageInfoDataModel()) }
                                }
                            }

                            SearchSectionId.CONTACTS -> {
                                Pager(
                                    config = PagingConfig(
                                        pageSize = CONTACT_LIST_PAGE_SIZE,
                                        enablePlaceholders = false
                                    ),
                                    pagingSourceFactory = { searchContactsUseCase(query.lowercase()) }
                                ).flow.map {
                                    it.map { item -> GlobalSearchListItem.ContactItem(item.toNewContactDataModel()) }
                                }
                            }
                        }

                        pagerFlow
                    }
                }
                .collectLatest { _pagingData.value = it }
        }
    }

    suspend fun getOrInsertSenderId(selectedItem: NewContactDataModel): Long {
        return smsRepository.getOrInsertSenderId(
            selectedItem.phoneNumber,
            countryCodeProvider.getMyCountryCode()
        )
    }
}