package com.summer.notifai.ui.home.contactlist

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertHeaderItem
import androidx.paging.map
import com.summer.core.android.sms.constants.Constants.CONTACT_LIST_PAGE_SIZE
import com.summer.core.android.sms.constants.Constants.SEARCH_NEW_CONTACT_ID
import com.summer.core.domain.repository.ISmsRepository
import com.summer.core.domain.usecase.GetContactListWithFilterUseCase
import com.summer.core.util.CountryCodeProvider
import com.summer.core.util.isValidPhoneNumber
import com.summer.notifai.ui.datamodel.NewContactDataModel
import com.summer.notifai.ui.datamodel.mapper.NewContactMapper.toNewContactDataModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class NewContactListViewModel @Inject constructor(
    private val smsRepository: ISmsRepository,
    private val getContactListWithFilterUseCase: GetContactListWithFilterUseCase,
    private val countryCodeProvider: CountryCodeProvider,
) : ViewModel() {

    val contactFilter = MutableLiveData("")

    @OptIn(FlowPreview::class)
    private val selectedQuery: StateFlow<String> = contactFilter.asFlow()
        .debounce(300)
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedContacts: StateFlow<PagingData<NewContactDataModel>> = selectedQuery
        .flatMapLatest { query -> getPagedSmsContacts(query.lowercase()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PagingData.empty())

    @OptIn(FlowPreview::class)
    private fun getPagedSmsContacts(query: String): Flow<PagingData<NewContactDataModel>> {
        val staticItem = if (query.isValidPhoneNumber()) {
            NewContactDataModel(
                id = SEARCH_NEW_CONTACT_ID.toLong(),
                contactName = "Send to this phone number",
                phoneNumber = query,
                icon = com.summer.core.R.drawable.ic_contact_24x24
            )
        } else null

        val pagingSource = Pager(
            config = PagingConfig(pageSize = CONTACT_LIST_PAGE_SIZE, enablePlaceholders = false),
            pagingSourceFactory = { getContactListWithFilterUseCase.invoke(query) }
        ).flow
            .map { pagingData -> pagingData.map { it.toNewContactDataModel() } }

        return if (staticItem != null) {
            pagingSource.map { pagingData ->
                pagingData.insertHeaderItem(item = staticItem)
            }
        } else {
            pagingSource
        }.debounce(300).cachedIn(viewModelScope)
    }

    suspend fun getOrInsertSenderId(selectedItem: NewContactDataModel): Long {
        return smsRepository.getOrInsertSenderId(
            selectedItem.phoneNumber,
            countryCodeProvider.getMyCountryCode()
        )
    }
}