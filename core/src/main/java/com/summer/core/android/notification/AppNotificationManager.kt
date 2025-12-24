package com.summer.core.android.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import com.summer.core.R
import com.summer.core.data.local.entities.SmsEntity
import com.summer.core.di.ChatSessionTracker
import com.summer.core.ui.model.SmsImportanceType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationIntentProvider: NotificationIntentProvider,
    private val chatSessionTracker: ChatSessionTracker
) {

    companion object {
        const val NOTIFICATION_ID_SUMMARY = 1001
        const val NOTIFICATION_ID_SMS_PROCESSING = 1002
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    fun createNotificationChannels() {
        val channels = NotificationChannelType.entries.map { channelType ->
            NotificationChannel(
                channelType.channelId,
                channelType.channelName,
                channelType.importance
            ).apply {
                description = channelType.description
                if (channelType == NotificationChannelType.SUMMARY) {
                    setSound(null, null)
                    enableVibration(false)
                }
            }
        }
        notificationManager?.createNotificationChannels(channels)
    }

    fun showNotificationForSmsProcessing(appContext: Context, contentText: String): Notification {
        val channelType = NotificationChannelType.SMS_PROCESSING
        return NotificationCompat.Builder(appContext, channelType.channelId)
            .setContentTitle(channelType.channelName)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_sms_sync_24x24)
            .setPriority(channelType.importance)
            .setOngoing(true)
            .build()
    }

    fun updateNotificationForSmsProcessing(appContext: Context, contentText: String) {
        val channelType = NotificationChannelType.SMS_PROCESSING
        val updatedNotification = NotificationCompat.Builder(appContext, channelType.channelId)
            .setContentTitle(channelType.channelName)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_sms_sync_24x24)
            .setPriority(channelType.importance)
            .setOngoing(true)
            .build()

        notificationManager?.notify(NOTIFICATION_ID_SMS_PROCESSING, updatedNotification)
    }

    fun showNotificationForSms(sms: SmsEntity) {
        if (sms.androidSmsId == null || chatSessionTracker.activeSenderAddressId == sms.senderAddressId) return
        val importance = sms.importanceScore ?: 3

        val channelType = when (importance) {
            5 -> NotificationChannelType.CRITICAL
            4 -> NotificationChannelType.IMPORTANT
            3 -> NotificationChannelType.GENERAL
            else -> return // Skip for importance 1 or 2
        }

        val notification = NotificationCompat.Builder(context, channelType.channelId)
            .setSmallIcon(R.drawable.ic_sms_sync_24x24)
            .setContentTitle("Message from ${sms.senderName ?: sms.rawAddress}")
            .setContentText(sms.body.take(60))
            .setContentIntent(
                notificationIntentProvider.provideSmsInboxPendingIntent(
                    senderAddressId = sms.senderAddressId,
                    smsImportanceType = SmsImportanceType.IMPORTANT
                )
            )
            .setPriority(channelType.importance)
            .setAutoCancel(true)
            .build()
        Log.d(
            "NotificationDebug",
            "Notifying for sms.androidSmsId=${sms.androidSmsId}, body=${sms.body.take(30)}"
        )
        notificationManager?.notify(sms.androidSmsId, notification)
    }

    fun clearNotificationForSender(androidSmsIds: List<Long>) {
        androidSmsIds.forEach {
            notificationManager?.cancel(it.toInt())
        }
    }

    fun showDailySummaryNotification(totalMessages: Int) {
        val notification = NotificationCompat.Builder(context, NotificationChannelType.SUMMARY.channelId)
            .setSmallIcon(android.R.drawable.star_on) //TODO(Replace with icon)
            .setContentTitle("Today's Messages")
            .setContentText("You received $totalMessages messages today.")
            .setContentIntent(notificationIntentProvider.provideSummaryPendingIntent())
            .setPriority(NotificationChannelType.SUMMARY.importance)
            .setAutoCancel(true)
            .build()

        notificationManager?.notify(NOTIFICATION_ID_SUMMARY, notification)
    }
}