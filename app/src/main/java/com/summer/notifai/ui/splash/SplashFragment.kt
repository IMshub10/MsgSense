package com.summer.notifai.ui.splash

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.summer.core.android.permission.manager.IPermissionManager
import com.summer.notifai.R
import com.summer.notifai.databinding.FragSplashBinding
import com.summer.notifai.ui.start.StartViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Splash/routing fragment that replaces StartActivity.
 * Determines which flow to launch based on onboarding state.
 */
@AndroidEntryPoint
class SplashFragment : Fragment() {

    private var _binding: FragSplashBinding? = null
    private val binding get() = _binding!!

    private val viewModel by viewModels<StartViewModel>()

    @Inject
    lateinit var permissionManager: IPermissionManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragSplashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner) { uiState ->
            if (uiState.hasAgreedToUserAgreement != null && uiState.isSmsProcessingCompleted != null) {
                navigateToNextDestination(uiState)
            }
        }
    }

    private fun navigateToNextDestination(uiState: com.summer.notifai.ui.start.StartUiState) {
        val navController = findNavController()

        // Prevent duplicate navigation
        if (navController.currentDestination?.id != R.id.splashFragment) return

        when {
            uiState.hasAgreedToUserAgreement == true && uiState.isSmsProcessingCompleted == true -> {
                // User has completed onboarding, go to home
                navController.navigate(R.id.action_splash_to_home)
            }

            uiState.hasAgreedToUserAgreement == true && !permissionManager.hasRequiredPermissions() -> {
                // User has agreed but hasn't granted required permissions, go to permissions
                navController.navigate(R.id.action_splash_to_permissions)
            }

            uiState.hasAgreedToUserAgreement == true && uiState.isSmsProcessingCompleted == false -> {
                // User has agreed and has permissions, but SMS processing not done
                navController.navigate(R.id.action_splash_to_smsProcessing)
            }

            else -> {
                // Navigate to onboarding flow - starts at user agreement
                navController.navigate(R.id.action_splash_to_onboarding)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
