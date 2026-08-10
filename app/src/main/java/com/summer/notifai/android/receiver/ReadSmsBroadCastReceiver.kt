package com.summer.notifai.android.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.summer.core.android.permission.manager.IPermissionManager
import com.summer.notifai.di.ReadSmsReceiverEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReadSmsBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("SMSBroadCastReceiver", intent?.dataString.orEmpty())
        if (context == null || intent == null) return

        if (intent.action == Telephony.Sms.Intents.SMS_DELIVER_ACTION) {
            val appContext = context.applicationContext
            val entryPoint = EntryPointAccessors.fromApplication(
                appContext,
                ReadSmsReceiverEntryPoint::class.java
            )

            val smsInserter = entryPoint.smsInserter()
            val permissionManager: IPermissionManager = entryPoint.permissionManager()
            val appNotificationManager = entryPoint.appNotificationManager()
            val isSenderBlockedUseCase = entryPoint.isSenderBlockedUseCase()
            val nerScheduler = entryPoint.nerScheduler()
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val sms = smsInserter.processIncomingSms(appContext, intent)
                    if (sms != null && permissionManager.hasSendNotifications()) {
                        val isBlocked = isSenderBlockedUseCase(sms.senderAddressId)
                        if (!isBlocked) appNotificationManager.showNotificationForSms(sms = sms)
                        else Log.d("SMSBroadCastReceiver", "Notification blocked: sender is blocked")
                    }
                    if (sms != null) nerScheduler.enqueueRealtime(sms)
                } catch (e : Exception) {
                    Log.e("SMSBroadCastReceiver", "Error processing incoming SMS", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
