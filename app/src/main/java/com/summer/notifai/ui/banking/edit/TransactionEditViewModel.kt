package com.summer.notifai.ui.banking.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.summer.core.data.repository.NerExtractionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NerExtractionRepository,
) : ViewModel() {
    val extractionId: Long = checkNotNull(savedStateHandle["extractionId"])
    val transaction = repository.observeTransaction(extractionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(
        merchant: String,
        amount: String,
        currency: String,
        direction: String,
        category: String,
        paymentMethod: String,
        onSaved: () -> Unit,
    ) {
        viewModelScope.launch {
            repository.saveOverride(
                extractionId, merchant, amount, currency.uppercase(), direction, category, paymentMethod,
            )
            onSaved()
        }
    }
}
