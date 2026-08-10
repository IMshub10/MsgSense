package com.summer.notifai.ui.banking.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.summer.ner.banking.BankingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: BankingRepository,
) : ViewModel() {
    val extractionId: Long = checkNotNull(savedStateHandle["extractionId"])
    val transaction = repository.observeTransaction(extractionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
