package com.summer.notifai

import android.annotation.SuppressLint
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.provider.ContactsContract
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.summer.core.BaseApp
import com.summer.core.android.sms.util.SendSmsActions
import com.summer.ner.NerBackfillBackstopWorker
import com.summer.notifai.android.observer.ContactObserver
import com.summer.notifai.di.ContactObserverDepsEntryPoint
import com.summer.notifai.android.receiver.SentSmsReceiver
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class App : BaseApp(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    private val smsSentReceiver = SentSmsReceiver()

    override fun onCreate() {
        super.onCreate()
        if (!isMainProcess()) return
        if (BuildConfig.DEBUG) setUpStrictMode()
        setUpSyncContacts()
        registerSentSmsReceiver()
        NerBackfillBackstopWorker.schedule(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerSentSmsReceiver() {
        val sentFilter = IntentFilter(SendSmsActions.ACTION_SMS_SENT.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smsSentReceiver, sentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(smsSentReceiver, sentFilter)
        }
    }

    private fun setUpSyncContacts() {
        if (permissionManager.hasReadContacts()) {
            val observer = registerContactObserver(this)
            observer.onChange(true)
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    super.onStart(owner)
                    observer.syncContacts()
                }
            })
        }
    }

    private fun registerContactObserver(context: Context): ContactObserver {
        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            ContactObserverDepsEntryPoint::class.java
        )
        val observer = entryPoint.contactObserver()
        context.contentResolver.registerContentObserver(
            ContactsContract.Contacts.CONTENT_URI,
            true,
            observer
        )
        return observer
    }
}
