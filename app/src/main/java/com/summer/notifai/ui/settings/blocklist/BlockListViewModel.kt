package com.summer.notifai.ui.settings.blocklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import com.summer.core.data.local.model.ContactInfoInboxModel
import com.summer.notifai.domain.usecase.SearchBlockedSendersUseCase
import com.summer.notifai.domain.usecase.UnblockSenderUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BlockListViewModel @Inject constructor(
    private val searchBlockedSendersUseCase: SearchBlockedSendersUseCase,
    private val unblockSenderUseCase: UnblockSenderUseCase
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val blockedSenders: StateFlow<PagingData<ContactInfoInboxModel>> =
        _searchQuery
            .debounce(300)
            .map { it.trim().lowercase() }
            .distinctUntilChanged()
            .flatMapLatest { query -> searchBlockedSendersUseCase(query) }
            .stateIn(viewModelScope, SharingStarted.Lazily, PagingData.empty())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun unblockSender(senderAddressId: Long, callback: () -> Unit) {
        viewModelScope.launch(Dispatchers.Default) {
            unblockSenderUseCase.invoke(senderAddressId)
            callback()
        }
    }

}