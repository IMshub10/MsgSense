package com.summer.core.android.sms.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.room.Dao
import com.summer.core.R
import com.summer.core.android.notification.NotificationChannelType
import com.summer.core.domain.model.FetchResult
import com.summer.core.data.repository.SmsRepository
import com.summer.core.domain.repository.ISmsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * @deprecated Use [SmsProcessingWorker] instead. This service is kept for reference and will be removed in future versions.
 * Migration path: Use WorkManager's OneTimeWorkRequest to schedule SMS processing tasks.
 */
@Deprecated(message = "Use SmsProcessingWorker instead for background processing")
@AndroidEntryPoint
class SmsProcessingService : Service() {

    @Inject
    lateinit var repository: ISmsRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        //TODO(setOngoing() for the notification)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                createNotification("Starting SMS Processing..."),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification("Starting SMS Processing..."))
        }
        startProcessing()
    }

    private fun startProcessing() {
        serviceScope.launch {
            repository.fetchSmsMessagesFromDevice().collect { result ->
                when (result) {
                    is FetchResult.Loading -> {
                        updateNotification("${result.processedCount}/${result.totalCount} processed")
                        sendBroadcast(FetchResult.Loading(result.processedCount, result.totalCount))
                    }

                    is FetchResult.Success -> {
                        updateNotification("Processing completed successfully!")
                        sendBroadcast(FetchResult.Success)
                        repository.setSmsProcessingStatusCompleted(true)
                        stopSelf()
                    }

                    is FetchResult.Error -> {
                        updateNotification("Error: ${result.exception.message}")
                        sendBroadcast(FetchResult.Error(result.exception))
                        stopSelf()
                    }
                }
            }
        }
    }

    @Synchronized
    private fun sendBroadcast(fetchResult: FetchResult) {
        val intent = Intent(ACTION_SMS_PROCESSING_UPDATE)
        intent.putExtra(EXTRA_FETCH_RESULT, fetchResult)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun createNotification(contentText: String): Notification {
        createNotificationChannel()

        return NotificationCompat.Builder(this, NotificationChannelType.SMS_PROCESSING.channelId)
            .setContentTitle("SMS Processing")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_sms_sync_24x24)
            .setPriority(NotificationChannelType.SMS_PROCESSING.importance)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NotificationChannelType.SMS_PROCESSING.channelId,
            NotificationChannelType.SMS_PROCESSING.channelName,
            NotificationChannelType.SMS_PROCESSING.importance
        ).apply {
            description = NotificationChannelType.SMS_PROCESSING.description
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.createNotificationChannel(channel)
    }

    private fun updateNotification(contentText: String) {
        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notification = createNotification(contentText)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 1
        const val ACTION_SMS_PROCESSING_UPDATE =
            "com.summer.core.android.sms.ACTION_SMS_PROCESSING_UPDATE"
        const val EXTRA_FETCH_RESULT = "extra_fetch_result"

        fun startService(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC
                    )
                    != PackageManager.PERMISSION_GRANTED
                ) {

                    Log.e("SmsProcessingService", "Permission not granted! Cannot start service.")
                    return
                }
            }

            val intent = Intent(context, SmsProcessingService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, SmsProcessingService::class.java)
            context.stopService(intent)
        }
    }
}