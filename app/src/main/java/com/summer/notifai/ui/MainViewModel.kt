package com.summer.notifai.ui

import androidx.lifecycle.ViewModel
import com.summer.core.domain.repository.IOnboardingRepository
import com.summer.core.domain.usecase.ShouldShowDefaultSmsPromptUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val onboardingRepository: IOnboardingRepository,
    private val shouldShowDefaultSmsPromptUseCase: ShouldShowDefaultSmsPromptUseCase
) : ViewModel() {

    /**
     * Check if conditions are met to prompt user to set this app as default SMS.
     * Delegates to [ShouldShowDefaultSmsPromptUseCase].
     *
     * @return true if the default SMS prompt should be shown
     */
    fun shouldPromptForDefaultSms(): Boolean {
        return shouldShowDefaultSmsPromptUseCase()
    }

    /**
     * Record that the default SMS prompt was shown.
     * Updates the timestamp in SharedPreferences.
     */
    fun onDefaultSmsPromptShown() {
        onboardingRepository.setDefaultSmsPromptLastShownTime(System.currentTimeMillis())
    }
}

