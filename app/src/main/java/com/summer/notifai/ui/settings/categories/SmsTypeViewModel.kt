package com.summer.notifai.ui.settings.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.summer.core.data.local.entities.SmsClassificationTypeEntity
import com.summer.notifai.domain.usecase.GetSmsTypesUseCase
import com.summer.notifai.domain.usecase.UpdateSmsTypeImportanceUseCase
import com.summer.notifai.ui.datamodel.SmsTypeUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class SmsTypeSettingsViewModel @Inject constructor(
    private val getSmsTypesUseCase: GetSmsTypesUseCase,
    private val updateSmsTypeImportanceUseCase: UpdateSmsTypeImportanceUseCase
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _smsTypeItems = MutableStateFlow<List<SmsTypeUiModel>>(emptyList())
    val smsTypeItems: StateFlow<List<SmsTypeUiModel>> = _smsTypeItems

    init {
        viewModelScope.launch {
            getSmsTypesUseCase().let { types ->
                _smsTypeItems.value = groupAndFormat(types)
            }
        }
    }

    fun updateQuery(newQuery: String) {
        _query.value = newQuery
        filterAndGroup()
    }

    private fun filterAndGroup() {
        viewModelScope.launch {
            val filtered = getSmsTypesUseCase().filter {
                it.smsType?.contains(_query.value, ignoreCase = true) == true
            }
            _smsTypeItems.value = groupAndFormat(filtered)
        }
    }

    private fun groupAndFormat(list: List<SmsClassificationTypeEntity>): List<SmsTypeUiModel> {
        return list
            .groupBy { it.compactSmsType ?: "Other" }
            .flatMap { (group, items) ->
                listOf(SmsTypeUiModel.Header(group)) + items.map { SmsTypeUiModel.Item(it) }
            }
    }

    fun updateImportance(id: Int, isImportant: Boolean) {
        viewModelScope.launch(Dispatchers.Default) {
            updateSmsTypeImportanceUseCase(id, isImportant)
        }
    }
}