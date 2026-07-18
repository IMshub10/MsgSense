package com.summer.notifai.ui.banking.account

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.summer.core.data.model.BankAccount
import com.summer.core.data.repository.NerExtractionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AccountDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NerExtractionRepository,
) : ViewModel() {
    val accountId: Long = checkNotNull(savedStateHandle["accountId"])
    val account = repository.observeAccount(accountId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val allAccounts = repository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun rename(name: String) = viewModelScope.launch { repository.renameAccount(accountId, name) }
    fun setHidden(hidden: Boolean) = viewModelScope.launch { repository.setAccountHidden(accountId, hidden) }
    fun assign(extractionId: Long, targetId: Long) =
        viewModelScope.launch { repository.assignTransaction(extractionId, targetId) }
    fun unassign(extractionId: Long) = viewModelScope.launch { repository.unassignTransaction(extractionId) }
    fun merge(target: BankAccount) = viewModelScope.launch { repository.mergeAccounts(accountId, target.id) }
}
