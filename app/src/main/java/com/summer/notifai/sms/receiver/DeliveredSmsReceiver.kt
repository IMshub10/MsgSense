package com.summer.notifai.sms.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.summer.core.android.sms.constants.SMSColumnNames
import com.summer.core.android.sms.util.SmsStatus
import com.summer.notifai.di.DeliveredSmsReceiverEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DeliveredSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val id = intent?.getLongExtra(SMSColumnNames.COLUMN_ID, -1) ?: return
        val address = intent.getStringExtra(SMSColumnNames.COLUMN_ADDRESS) ?: return

        val status = when (resultCode) {
            Activity.RESULT_OK -> {
                Log.d("DeliveredSmsReceiver", "SMS delivered to $address")
                Toast.makeText(context, "SMS delivered to $address", Toast.LENGTH_SHORT).show()
                SmsStatus.DELIVERED
            }

            else -> {
                Log.e("DeliveredSmsReceiver", "Failed to deliver SMS to $address")
                Toast.makeText(context, "Failed to deliver SMS to $address", Toast.LENGTH_SHORT)
                    .show()
                SmsStatus.DELIVERY_FAILED
            }
        }

        val appContext = context.applicationContext
        val entryPoint = EntryPointAccessors.fromApplication(
            appContext,
            DeliveredSmsReceiverEntryPoint::class.java
        )
        val markSmsAsDeliveredUseCase = entryPoint.markSmsAsDelivered()

        CoroutineScope(Dispatchers.IO).launch {
            markSmsAsDeliveredUseCase(context, id, status = status)
        }
    }
}