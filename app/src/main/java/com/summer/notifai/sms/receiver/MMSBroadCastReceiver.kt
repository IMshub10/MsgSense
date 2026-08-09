package com.summer.notifai.sms.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class MMSBroadCastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("SMSBroadCastReceiver", intent?.dataString.orEmpty())
    }
}