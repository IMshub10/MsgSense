package com.summer.notifai.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.summer.core.android.sms.constants.Constants.CONTACT_LIST_PAGE_SIZE
import com.summer.core.android.sms.constants.Constants.SMS_PROCESSING_WORK_NAME
import com.summer.core.android.sms.model.SmsProcessingStatus
import com.summer.core.domain.usecase.GetSmsContactListByImportanceUseCase
import com.summer.notifai.ui.datamodel.ContactMessageInfoDataModel
import com.summer.notifai.ui.datamodel.mapper.ContactInfoMapper.toContactMessageInfoDataModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    private val getSmsContactListByImportanceUseCase: GetSmsContactListByImportanceUseCase
) : AndroidViewModel(application) {

    // Filter toggle state
    val isImportant = MutableLiveData(true)

    // Worker running state
    private val _isWorkerRunning = MutableStateFlow(false)
    val isWorkerRunning: StateFlow<Boolean> = _isWorkerRunning.asStateFlow()

    // Sync progress (processed/total)
    data class SyncProgress(val processed: Int, val total: Int)
    private val _syncProgress = MutableStateFlow<SyncProgress?>(null)
    val syncProgress: StateFlow<SyncProgress?> = _syncProgress.asStateFlow()

    init {
        observeWorkerState(application)
    }

    private fun observeWorkerState(application: Application) {
        WorkManager.getInstance(application)
            .getWorkInfosForUniqueWorkLiveData(SMS_PROCESSING_WORK_NAME)
            .observeForever { workInfos ->
                val runningWork = workInfos?.find { it.state == WorkInfo.State.RUNNING }
                val isRunning = runningWork != null
                
                _isWorkerRunning.value = isRunning
                
                // Extract progress from running worker
                if (runningWork != null) {
                    val processed = runningWork.progress.getInt(SmsProcessingStatus.PROCESSED_COUNT_KEY, 0)
                    val total = runningWork.progress.getInt(SmsProcessingStatus.TOTAL_COUNT_KEY, 0)
                    if (total > 0) {
                        _syncProgress.value = SyncProgress(processed, total)
                    }
                } else {
                    _syncProgress.value = null
                }
            }
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val pagedContacts: Flow<PagingData<ContactMessageInfoDataModel>> = isImportant.asFlow()
        .flatMapLatest { filter ->
            getPagedSmsContacts(filter)
        }
        .cachedIn(viewModelScope)

    private fun getPagedSmsContacts(isImportant: Boolean): Flow<PagingData<ContactMessageInfoDataModel>> {
        return Pager(
            config = PagingConfig(pageSize = CONTACT_LIST_PAGE_SIZE, enablePlaceholders = false),
            pagingSourceFactory = {
                getSmsContactListByImportanceUseCase.invoke(isImportant)
            }
        ).flow
            .map { pagingData ->
                pagingData.map { contact ->
                    contact.toContactMessageInfoDataModel()
                }
            }
    }
}