package com.summer.notifai.ui.onboarding.useragreement

import android.os.Bundle
import android.view.View
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.summer.core.domain.repository.IOnboardingRepository
import com.summer.notifai.R
import com.summer.notifai.databinding.FragUserAgreementBinding
import com.summer.notifai.ui.onboarding.OnboardingViewModel
import com.summer.core.ui.BaseFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class UserAgreementFrag : BaseFragment<FragUserAgreementBinding>() {
    override val layoutResId: Int
        get() = R.layout.frag_user_agreement

    private val onboardingViewModel: OnboardingViewModel by activityViewModels()

    @Inject
    lateinit var onboardingRepository: IOnboardingRepository

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Check if user already agreed - skip to next step
        if (onboardingRepository.hasAgreedToUserAgreement()) {
            if (findNavController().currentDestination?.id == R.id.userAgreementFrag) {
                findNavController().navigate(R.id.action_userAgreementFrag_to_permissionsFrag)
            }
            return
        }
        
        listeners()
    }

    private fun listeners() {
        with(mBinding) {
            fragUserAgreementActionButton.setOnClickListener {
                if (fragUserAgreementActionButton.text == getString(R.string.agree_to_use)) {
                    onboardingViewModel.onOptionalDataSharingDisabled()
                    if (findNavController().currentDestination?.id == R.id.userAgreementFrag)
                        findNavController().navigate(R.id.action_userAgreementFrag_to_permissionsFrag)
                } else {
                    scrollToBottom()
                }
            }
            fragUserAgreementScrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { v, _, _, _, _ ->
                if (v.getChildAt(0).bottom <= (v.height + v.scrollY)) {
                    fragUserAgreementActionButton.text = getString(R.string.agree_to_use)
                }
            })
        }
    }

    private fun scrollToBottom(){
        with(mBinding){
            fragUserAgreementScrollView.smoothScrollTo(
                0,
                fragUserAgreementScrollView.getChildAt(0).bottom
            )
        }
    }

}