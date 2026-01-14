package com.summer.notifai.di

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.navigation.NavDeepLinkBuilder
import com.summer.core.android.notification.Constants.REQUEST_CODE_SUMMARY_NOTIFICATION
import com.summer.core.android.notification.NotificationIntentProvider
import com.summer.core.ui.model.SmsImportanceType
import com.summer.notifai.R
import com.summer.notifai.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationIntentProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : NotificationIntentProvider {
    override fun provideSummaryPendingIntent(): PendingIntent {
        // Navigate to MainActivity which starts at home
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        return PendingIntent.getActivity(
            context,
            REQUEST_CODE_SUMMARY_NOTIFICATION,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun provideSmsInboxPendingIntent(
        senderAddressId: Long,
        smsImportanceType: SmsImportanceType
    ): PendingIntent {
        // Use NavDeepLinkBuilder for proper navigation to inbox fragment
        return NavDeepLinkBuilder(context)
            .setGraph(R.navigation.nav_main)
            .setDestination(R.id.smsInboxFragment)
            .setArguments(
                android.os.Bundle().apply {
                    putLong("senderAddressId", senderAddressId)
                    putInt("smsImportanceType", smsImportanceType.value)
                }
            )
            .setComponentName(MainActivity::class.java)
            .createPendingIntent()
    }

    override fun provideSmsProcessingPendingIntent(): PendingIntent {
        return NavDeepLinkBuilder(context)
            .setGraph(R.navigation.nav_main)
            .setComponentName(MainActivity::class.java)
            .setDestination(R.id.splashFragment)
            .createPendingIntent()
    }
}