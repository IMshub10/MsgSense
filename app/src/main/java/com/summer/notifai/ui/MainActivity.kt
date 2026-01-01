package com.summer.notifai.ui

import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.summer.core.android.permission.manager.IPermissionManager
import com.summer.core.di.PrewarmManager
import com.summer.core.ui.BaseActivity
import com.summer.core.util.showShortToast
import com.summer.notifai.R
import com.summer.notifai.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Single Activity host for the entire app.
 * Manages navigation, deep links, and default SMS app prompt.
 */
@AndroidEntryPoint
class MainActivity : BaseActivity<ActivityMainBinding>() {
    
    override val layoutResId: Int
        get() = R.layout.activity_main

    private var navController: NavController? = null

    @Inject
    lateinit var permissionManager: IPermissionManager
    
    @Inject
    lateinit var prewarmManager: PrewarmManager

    private val mainViewModel: MainViewModel by viewModels()

    private val defaultSmsAppLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult? ->
        if (result?.resultCode == RESULT_OK) {
            showShortToast("Permission set as default")
        } else {
            showShortToast("App must be set as default SMS app to function properly.")
            fallbackToLegacyIntent()
        }
    }

    override fun onActivityReady(savedInstanceState: Bundle?) {
        setupNavController()
        handleIntent(intent)
        checkAndPromptDefaultSmsApp()
        
        // Prewarm SMS inbox dependencies in background
        lifecycleScope.launch { prewarmManager.prewarm() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun setupNavController() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.fcv_main_navHost) as? NavHostFragment
        navController = navHostFragment?.navController
            ?: throw IllegalStateException("NavController is null")
    }

    /**
     * Handle SMS deep links (sms:, smsto:, mms:, mmsto:)
     * Navigates with clear backstack as per Option A.
     */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val action = intent.action
        val data = intent.data

        when (action) {
            Intent.ACTION_VIEW, Intent.ACTION_SEND, Intent.ACTION_SENDTO -> {
                data?.let { handleSmsUri(it) }
            }
        }
    }

    private fun handleSmsUri(uri: Uri) {
        val scheme = uri.scheme
        if (scheme in arrayListOf("sms", "smsto", "mms", "mmsto")) {
            val phoneNumber = uri.schemeSpecificPart?.takeIf { it.isNotBlank() }
            phoneNumber?.let {
                // Navigate to inbox with the phone number
                // This will be wired up once we complete SafeArgs setup
                // For now, log intent received
            }
        }
    }

    /**
     * Check if conditions are met to prompt user to set this app as default SMS.
     * Delegates logic to MainViewModel.
     */
    private fun checkAndPromptDefaultSmsApp() {
        if (mainViewModel.shouldPromptForDefaultSms()) {
            mainViewModel.onDefaultSmsPromptShown()
            promptToSetDefaultSmsApp()
        }
    }

    /**
     * Prompt to set this app as the default SMS app.
     * Called by fragments when needed.
     */
    fun promptToSetDefaultSmsApp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java) as RoleManager
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
            defaultSmsAppLauncher.launch(intent)
        } else {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
            startActivity(intent)
        }
    }

    private fun fallbackToLegacyIntent() {
        val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
        intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
        startActivity(intent)
    }
}

