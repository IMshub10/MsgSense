package com.summer.notifai.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.net.toUri
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.summer.core.ui.BaseFragment
import com.summer.notifai.R
import com.summer.notifai.databinding.FragSettingsBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsFrag : BaseFragment<FragSettingsBinding>() {

    override val layoutResId: Int
        get() = R.layout.frag_settings

    private val viewmodel: SettingsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initListeners()
        setupStoreInSystemToggle()
    }

    private fun initListeners() {
        mBinding.ivFragSettingsBack.setOnClickListener {
            findNavController().popBackStack()
        }
        mBinding.tvFragSettingsItemBlockList.setOnClickListener {
            if (findNavController().currentDestination?.id == R.id.settingsFrag)
                findNavController().navigate(SettingsFragDirections.actionSettingsFragToBlockListFrag())
        }
        mBinding.tvFragSettingsItemSmsCategories.setOnClickListener {
            if (findNavController().currentDestination?.id == R.id.settingsFrag)
                findNavController().navigate(SettingsFragDirections.actionSettingsFragToSmsTypeSettingsFrag())
        }
        mBinding.tvFragSettingsItemPrivacy.setOnClickListener {
            if (findNavController().currentDestination?.id == R.id.settingsFrag) {
                navigateToPrivacyPolicyPage()
            }
        }
    }

    private fun navigateToPrivacyPolicyPage() {
        val url = "https://github.com/IMshub10/MsgSense/wiki"
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }

    private fun setupStoreInSystemToggle() {
        viewmodel.storeInSystemSms.observe(viewLifecycleOwner) { isChecked ->
            mBinding.switchFragSettingsStoreInSystem.isChecked = isChecked
        }

        mBinding.switchFragSettingsStoreInSystem.setOnCheckedChangeListener { _, isChecked ->
            viewmodel.updateStoreInSystemSms(isChecked)
        }

        viewmodel.loadStoreInSystemSmsPreference()
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }
}