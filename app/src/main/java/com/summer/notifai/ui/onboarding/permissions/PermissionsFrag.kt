package com.summer.notifai.ui.onboarding.permissions

import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Telephony
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.navigation.fragment.findNavController
import com.summer.core.android.permission.manager.IPermissionManager
import com.summer.core.util.showShortToast
import com.summer.notifai.R
import com.summer.notifai.databinding.FragPermissionsBinding
import com.summer.notifai.ui.datamodel.PermissionItemModel
import com.summer.core.ui.BaseFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PermissionsFrag : BaseFragment<FragPermissionsBinding>() {
    override val layoutResId: Int
        get() = R.layout.frag_permissions

    @Inject
    lateinit var permissionManager: IPermissionManager

    // Request Default SMS App
    private val defaultSmsAppLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult? ->
        if (result?.resultCode == Activity.RESULT_OK) {
            requestRequiredPermissions() // Now request SMS permissions
        } else {
            showShortToast("App must be set as default SMS app to function properly.")
        }
    }

    // Request Permissions Launcher
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val deniedPermissions = permissions.filterValues { !it }.keys
            if (deniedPermissions.isEmpty()) {
                showShortToast("All necessary permissions granted!")
                navigateToSmsProcessing()
            } else {
                showRationaleOrSettings(deniedPermissions)
            }
        }

    /**
     * Step 1: Ask the user to set this app as the default SMS app.
     */
    private fun promptToSetDefaultSmsApp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager =
                requireContext().getSystemService(RoleManager::class.java) as RoleManager
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
            defaultSmsAppLauncher.launch(intent)
        } else {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, requireContext().packageName)
            startActivity(intent)
        }
    }

    /**
     * Step 2: Once the app is the default SMS app, request all required permissions.
     */
    private fun requestRequiredPermissions() {
        val missingPermissions = permissionManager.requiredPermissions.filter {
            ContextCompat.checkSelfPermission(
                requireContext(),
                it
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            navigateToSmsProcessing()
            showShortToast("All permissions are already granted!")
        }
    }

    /**
     * Navigate to SMS processing - handles both onboarding flow and direct access paths.
     */
    private fun navigateToSmsProcessing() {
        val currentDestId = findNavController().currentDestination?.id
        when (currentDestId) {
            R.id.permissionsFrag -> {
                // From onboarding flow
                findNavController().navigate(R.id.action_permissionsFrag_to_smsProcessingFrag)
            }
            R.id.permissionsFragDirect -> {
                // From direct splash navigation
                findNavController().navigate(R.id.action_permissionsDirect_to_smsProcessing)
            }
        }
    }

    /**
     * Step 3: Show rationale if the user denied permissions before.
     */
    private fun showRationaleOrSettings(deniedPermissions: Set<String>) {
        val shouldShowRationale = deniedPermissions.any {
            ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), it)
        }

        if (shouldShowRationale) {
            showShortToast("Permissions are required for core features. Please allow them.")
            permissionLauncher.launch(deniedPermissions.toTypedArray())
        } else {
            showShortToast("Please enable permissions manually from settings.")
            openAppSettings()
        }
    }

    /**
     * Step 4: Open App Settings if permissions are permanently denied.
     */
    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:${requireContext().packageName}".toUri()
        }
        startActivity(intent)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Check if all permissions already granted - skip to next step
        if (permissionManager.hasRequiredPermissions()) {
            navigateToSmsProcessing()
            return
        }
        
        setRecyclerview()
        listeners()
    }

    private fun listeners() {
        with(mBinding) {
            fragUserAgreementActionButton.setOnClickListener {
                //TODO("Not sure if we should ask for default SMS app permission here")
                /*if (!permissionManager.isDefaultSms())
                    promptToSetDefaultSmsApp()
                else*/
                requestRequiredPermissions()
            }
        }
    }

    private fun setRecyclerview() {
        val titlesArray = resources.getStringArray(R.array.permission_titles)
        val descriptionsArray = resources.getStringArray(R.array.permission_description)
        val iconsArray = intArrayOf(
            R.drawable.ic_sms_24x24,
            R.drawable.ic_contacts_24x24,
            R.drawable.ic_storage_24x24,
            R.drawable.ic_storage_24x24
        )

        val permissions = titlesArray.indices.map { index ->
            PermissionItemModel(
                icon = iconsArray[index],
                title = titlesArray[index],
                description = descriptionsArray[index],
                isFirst = index == 0,
                isLast = index == titlesArray.size - 1
            )
        }

        mBinding.rvPermissions.adapter = PermissionsAdapter(permissions)
    }

}