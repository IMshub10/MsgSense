package com.summer.notifai.android.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.summer.core.android.sms.constants.SMSColumnNames
import com.summer.core.android.sms.util.SmsStatus
import com.summer.notifai.di.SentSmsReceiverEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SentSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val id = intent?.getLongExtra(SMSColumnNames.COLUMN_ID, -1) ?: return
        val address = intent.getStringExtra(SMSColumnNames.COLUMN_ADDRESS) ?: return

        val status = when (resultCode) {
            Activity.RESULT_OK -> {
                Log.d("SentSmsReceiver", "SMS sent to $address (id=$id)")
                Toast.makeText(context, "SMS sent to $address", Toast.LENGTH_SHORT).show()
                SmsStatus.SENT
            }
            else -> {
                Log.e("SentSmsReceiver", "Failed to send SMS to $address (id=$id)")
                Toast.makeText(context, "Failed to send SMS to $address", Toast.LENGTH_SHORT).show()
                SmsStatus.FAILED
            }
        }

        val appContext = context.applicationContext
        val entryPoint = EntryPointAccessors.fromApplication(appContext, SentSmsReceiverEntryPoint::class.java)
        val markSmsAsSentUseCase = entryPoint.markSmsAsSent()

        CoroutineScope(Dispatchers.IO).launch {
            markSmsAsSentUseCase(context, id, status = status)
        }
    }
}