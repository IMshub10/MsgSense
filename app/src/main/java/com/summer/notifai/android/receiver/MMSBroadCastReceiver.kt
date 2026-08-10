package com.summer.notifai.android.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class MMSBroadCastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("SMSBroadCastReceiver", intent?.dataString.orEmpty())
    }
}