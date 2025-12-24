package com.summer.notifai.ui.onboarding.processor

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.summer.core.android.phone.processor.ContactProcessor
import com.summer.core.android.sms.constants.Constants.SMS_PROCESSING_WORK_NAME
import com.summer.core.android.sms.model.SmsProcessingError
import com.summer.core.android.sms.model.SmsProcessingStatus
import com.summer.core.android.sms.service.SmsProcessingWorker
import com.summer.core.domain.model.FetchResult
import com.summer.core.ui.BaseFragment
import com.summer.core.util.ResultState
import com.summer.core.util.showShortToast
import com.summer.notifai.R
import com.summer.notifai.databinding.FragSmsProcessingBinding
import com.summer.notifai.ui.onboarding.OnboardingViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SmsProcessingFrag : BaseFragment<FragSmsProcessingBinding>() {
    override val layoutResId: Int
        get() = R.layout.frag_sms_processing

    private var navigateToHomeTriggered = false
    private val onboardingViewModel: OnboardingViewModel by activityViewModels()

    override fun onFragmentReady(instanceState: Bundle?) {
        observeViewModel()
        listeners()
    }

    private fun listeners() {
        mBinding.mbFragSmsProcessingContinue.setOnClickListener {
            // Check if we're in error state
            if (mBinding.mbFragSmsProcessingContinue.text == getString(R.string.retry)) {
                // Cancel any existing work before retrying
                WorkManager.getInstance(requireContext()).cancelUniqueWork(SMS_PROCESSING_WORK_NAME)
                startSmsProcessing()
            } else {
                navigateToHome()
            }
        }
    }

    private fun observeViewModel() {
        WorkManager.getInstance(requireContext())
            .getWorkInfosForUniqueWorkLiveData(SMS_PROCESSING_WORK_NAME)
            .observe(viewLifecycleOwner) { workInfoList ->
                workInfoList?.firstOrNull()?.let { workInfo ->
                    when (workInfo.state) {
                        WorkInfo.State.RUNNING -> {
                            handleRunningState(workInfo)
                        }

                        WorkInfo.State.SUCCEEDED -> {
                            onboardingViewModel.updateFetchResult(FetchResult.Success)
                        }

                        WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                            SmsProcessingError.fromWorkInfo(workInfo.state)?.let { error ->
                                onboardingViewModel.updateFetchResult(
                                    FetchResult.Error(Exception(error.userMessage))
                                )
                            }
                        }

                        else -> {
                            Log.e("SmsProcessingFrag", "Current Work State: ${workInfo.state}")
                        }
                    }
                }
            }

        onboardingViewModel.fetchStatus.observe(viewLifecycleOwner) { result ->
            when (result) {
                is FetchResult.Loading -> {
                    showLoadingState(result.processedCount, result.totalCount)
                }

                is FetchResult.Success -> {
                    showSuccessState()
                }

                is FetchResult.Error -> {
                    showErrorState()
                }
            }
        }
        onboardingViewModel.contactsSync.observe(viewLifecycleOwner) {
            when (it) {
                is ResultState.Failed -> {
                    showShortToast(message = getString(R.string.something_went_wrong))
                }

                ResultState.InProgress -> {}
                is ResultState.Success<*> -> {
                    startSmsProcessing()
                }
            }
        }
    }

    private fun handleRunningState(workInfo: WorkInfo) {
        val progress = workInfo.progress
        val status = SmsProcessingStatus.fromString(
            progress.getString(SmsProcessingStatus.STATUS_KEY)
        )
        when (status) {
            SmsProcessingStatus.Loading -> {
                val processed = progress.getInt(SmsProcessingStatus.PROCESSED_COUNT_KEY, 0)
                val total = progress.getInt(SmsProcessingStatus.TOTAL_COUNT_KEY, 1)
                onboardingViewModel.updateFetchResult(
                    FetchResult.Loading(processed, total)
                )
            }

            SmsProcessingStatus.Success -> {
                onboardingViewModel.updateFetchResult(FetchResult.Success)
            }

            SmsProcessingStatus.Error -> {
                val error = progress.getString(SmsProcessingStatus.ERROR_MESSAGE_KEY)
                    ?: getString(R.string.processing_error_generic)
                onboardingViewModel.updateFetchResult(
                    FetchResult.Error(Exception(error))
                )
            }
        }
    }

    private fun showLoadingState(processedCount: Int, totalCount: Int) {
        with(mBinding) {
            pgProgressIndicator.visibility = View.VISIBLE
            val progress = ((processedCount.toFloat() / totalCount) * 100).toInt()
            pgProgressIndicator.progress = progress
            tvStatusIndicator.apply {
                text = getString(R.string.progress_percentage, progress)
                visibility = View.VISIBLE
            }
        }
    }

    private fun showSuccessState() {
        with(mBinding) {
            pgProgressIndicator.progress = 100
            tvStatusIndicator.apply {
                text = getString(R.string.processing_complete)
                visibility = View.GONE
            }
            mbFragSmsProcessingContinue.visibility = View.GONE
        }
        navigateToHome()
    }

    private fun showErrorState() {
        with(mBinding) {
            pgProgressIndicator.visibility = View.GONE
            tvStatusIndicator.apply {
                text = getString(R.string.processing_error_generic)
                visibility = View.VISIBLE
            }
            mbFragSmsProcessingContinue.apply {
                text = getString(R.string.retry)
                visibility = View.VISIBLE
            }
        }


    }

    override fun onResume() {
        super.onResume()
        if (!onboardingViewModel.areContactsSynced()) {
            lifecycleScope.launch {
                ContactProcessor(requireContext()).fetchContacts()
                    .collectLatest { state ->
                        when (state) {
                            is ResultState.Failed -> {
                                showShortToast(message = getString(R.string.something_went_wrong))
                            }

                            ResultState.InProgress -> {}
                            is ResultState.Success -> {
                                onboardingViewModel.syncContacts(state.data)
                            }
                        }
                    }
            }
        } else {
            startSmsProcessing()
        }
    }

    private fun startSmsProcessing() {
        mBinding.mbFragSmsProcessingContinue.isVisible = true
        val workManager = WorkManager.getInstance(requireContext())
        val smsProcessingWork = OneTimeWorkRequestBuilder<SmsProcessingWorker>().build()

        workManager.beginUniqueWork(
            SMS_PROCESSING_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            smsProcessingWork
        ).enqueue()
    }

    private fun navigateToHome() {
        if (navigateToHomeTriggered) return
        navigateToHomeTriggered = true
        
        // Use Navigation component for single activity architecture
        // Check which destination we're at and use the correct action
        val navController = findNavController()
        val actionId = when (navController.currentDestination?.id) {
            R.id.smsProcessingFragDirect -> R.id.action_smsProcessingDirect_to_home
            else -> R.id.action_smsProcessing_to_home
        }
        navController.navigate(actionId)
    }
}