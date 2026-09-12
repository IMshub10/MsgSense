package com.summer.notifai.ui.banking.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.summer.core.data.model.BankingOverview
import com.summer.core.data.model.CategoryTotal
import com.summer.core.data.model.CurrencyTotals
import com.summer.ner.banking.BankingRepository
import com.summer.ner.banking.BankAccountOrganizationScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BankingHomeViewModel @Inject constructor(
    private val repository: BankingRepository,
    accountOrganizationScheduler: BankAccountOrganizationScheduler,
) : ViewModel() {
    val overview = repository.observeOverview()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BankingOverview())

    private val range = MutableStateFlow(periodRange(Period.MONTH))
    val insights = range.flatMapLatest { repository.observeInsights(it.first, it.second) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<CurrencyTotals>() to emptyList<CategoryTotal>())

    init {
        accountOrganizationScheduler.enqueue()
    }

    fun setPeriod(period: Period) {
        range.value = periodRange(period)
    }

    fun setCustomRange(start: Long, endInclusive: Long) {
        range.value = start to endInclusive + 1
    }

    private fun periodRange(period: Period): Pair<Long, Long> {
        val end = System.currentTimeMillis() + 1
        val calendar = Calendar.getInstance()
        when (period) {
            Period.DAY -> calendar.add(Calendar.DAY_OF_YEAR, -1)
            Period.MONTH -> calendar.add(Calendar.MONTH, -1)
            Period.YEAR -> calendar.add(Calendar.YEAR, -1)
            Period.CUSTOM -> calendar.add(Calendar.DAY_OF_YEAR, -30)
        }
        return calendar.timeInMillis to end
    }

    enum class Period { DAY, MONTH, YEAR, CUSTOM }
}
