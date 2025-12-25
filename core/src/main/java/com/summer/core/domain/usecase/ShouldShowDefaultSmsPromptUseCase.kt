package com.summer.core.domain.usecase

import com.summer.core.android.permission.manager.IPermissionManager
import com.summer.core.domain.repository.IOnboardingRepository
import javax.inject.Inject

/**
 * UseCase to determine if the default SMS app prompt should be shown.
 * 
 * Conditions checked:
 * 1. User has agreed to user agreement
 * 2. User has granted all required permissions
 * 3. App is not already the default SMS app
 * 4. At least 8 hours have passed since the last prompt
 */
class ShouldShowDefaultSmsPromptUseCase @Inject constructor(
    private val onboardingRepository: IOnboardingRepository,
    private val permissionManager: IPermissionManager
) {
    companion object {
        private const val EIGHT_HOURS_IN_MILLIS = 8 * 60 * 60 * 1000L
    }

    operator fun invoke(): Boolean {
        // Check if user has completed onboarding (agreed to terms + granted permissions)
        if (!onboardingRepository.hasAgreedToUserAgreement()) return false
        if (!permissionManager.hasRequiredPermissions()) return false

        // Check if app is already the default SMS app
        if (permissionManager.isDefaultSms()) return false

        // Check if 8 hours have passed since last prompt
        val lastPromptTime = onboardingRepository.getDefaultSmsPromptLastShownTime()
        val currentTime = System.currentTimeMillis()
        return !(lastPromptTime != 0L && (currentTime - lastPromptTime) < EIGHT_HOURS_IN_MILLIS)
    }
}
