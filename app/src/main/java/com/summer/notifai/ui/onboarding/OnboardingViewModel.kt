package com.summer.notifai.ui.onboarding

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.summer.core.domain.model.FetchResult
import com.summer.core.data.local.entities.ContactEntity
import com.summer.notifai.domain.repository.IOnboardingRepository
import com.summer.notifai.domain.usecase.SyncContactsUseCase
import com.summer.core.util.ResultState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val onboardingRepository: IOnboardingRepository,
    private val syncContactsUseCase: SyncContactsUseCase
) : ViewModel() {

    private val _fetchStatus = MutableLiveData<FetchResult>()
    val fetchStatus: LiveData<FetchResult> get() = _fetchStatus

    private val _contactsSync = MutableLiveData<ResultState<Int>>()
    val contactsSync: LiveData<ResultState<Int>> get() = _contactsSync

    private fun setUserAgreement() {
        viewModelScope.launch {
            onboardingRepository.setUserAgreement(true)
        }
    }

    private fun setDataSharing(enabled: Boolean) {
        viewModelScope.launch {
            onboardingRepository.setDataSharing(enabled)
        }
    }

    fun onOptionalDataSharingEnabled() {
        setUserAgreement()
        setDataSharing(true)
    }

    fun onOptionalDataSharingDisabled() {
        setUserAgreement()
        setDataSharing(false)
    }

    fun updateFetchResult(result: FetchResult) {
        _fetchStatus.postValue(result)
    }

    fun areContactsSynced(): Boolean {
        return onboardingRepository.areContactsSynced()
    }

    fun syncContacts(contacts: List<ContactEntity>) {
        syncContactsUseCase.execute(contacts).map {
            withContext(Dispatchers.Main) {
                when (it) {
                    is ResultState.Failed -> {
                        _contactsSync.postValue(it)
                    }

                    ResultState.InProgress -> {
                        _contactsSync.postValue(it)
                    }

                    is ResultState.Success<*> -> {
                        _contactsSync.postValue(it)
                    }
                }
            }
        }.launchIn(viewModelScope)
    }
}