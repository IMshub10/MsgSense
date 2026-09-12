package com.summer.notifai.ui.banking.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.summer.core.data.local.model.NerProcessingSummary
import com.summer.core.data.model.BankingTransaction
import com.summer.ner.banking.BankingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class TransactionListViewModel @Inject constructor(
    repository: BankingRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(Filter.ALL)

    val uiState = combine(
        repository.observeTransactions(),
        repository.observeProcessingSummary(),
        query,
        filter,
    ) { items, summary, query, filter ->
        val filteredItems = items.filter { item ->
            val matchesQuery = query.isBlank() ||
                item.merchant.contains(query, ignoreCase = true) ||
                item.bank?.contains(query, ignoreCase = true) == true ||
                item.maskedAccount?.contains(query, ignoreCase = true) == true
            matchesQuery && when (filter) {
                Filter.ALL -> true
                Filter.NEEDS_REVIEW -> item.reviewState == "NEEDS_REVIEW"
                Filter.EXPENSE -> item.direction == "DEBIT"
                Filter.INCOME -> item.direction in setOf("CREDIT", "REVERSAL")
            }
        }
        UiState(
            transactions = filteredItems,
            summary = summary,
            filtersApplied = query.isNotBlank() || filter != Filter.ALL,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun setQuery(value: String) {
        query.value = value.trim()
    }

    fun setFilter(value: Filter) {
        filter.value = value
    }

    data class UiState(
        val transactions: List<BankingTransaction> = emptyList(),
        val summary: NerProcessingSummary = NerProcessingSummary(0, 0, 0, 0, 0),
        val filtersApplied: Boolean = false,
    )

    enum class Filter { ALL, NEEDS_REVIEW, EXPENSE, INCOME }
}
