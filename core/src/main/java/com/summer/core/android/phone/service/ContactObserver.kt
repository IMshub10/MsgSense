package com.summer.core.android.phone.service

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.summer.core.android.permission.manager.IPermissionManager
import com.summer.core.android.phone.processor.ContactProcessor
import com.summer.core.android.phone.data.entity.ContactEntity
import com.summer.core.di.ContactObserverEntryPoint
import com.summer.core.exception.MissingPermissionException
import com.summer.core.domain.repository.IContactRepository
import com.summer.core.domain.usecase.SyncContactsUseCase
import com.summer.core.util.ResultState
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactObserver
@Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionManager: IPermissionManager
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        Log.d("ContactObserver", "Contacts changed at URI: $uri")
        syncContacts()
    }

    fun syncContacts() {
        if (!permissionManager.hasReadContacts()) {
            FirebaseCrashlytics.getInstance()
                .recordException(MissingPermissionException(exceptionMessage = "Missing READ_CONTACTS"))
            return
        }
        val hiltEntryPoint =
            EntryPointAccessors.fromApplication(context, ContactObserverEntryPoint::class.java)
        val repository = hiltEntryPoint.contactRepository()
        val syncContactsUseCase = hiltEntryPoint.syncContactsUseCase()

        scope.launch {
            try {
                checkForRecentNameChanges(syncContactsUseCase, repository)
            } catch (e: Exception) {
                Log.e("ContactSync", "Error syncing contacts", e)
            }
        }
    }

    private suspend fun checkForRecentNameChanges(
        syncContactsUseCase: SyncContactsUseCase,
        repository: IContactRepository
    ) {
        val lastCheckedTimestamp = repository.getPhoneTableLastUpdated()
        val contactProcessor = ContactProcessor(context)

        contactProcessor.fetchContacts(lastCheckedTimestamp).collect { result ->
            when (result) {
                is ResultState.InProgress -> Log.d(
                    "ContactSync",
                    "Fetching contacts in progress..."
                )

                is ResultState.Success -> {
                    val updatedContacts = result.data
                    if (updatedContacts.isNotEmpty()) {
                        syncContactsUseCase.execute(updatedContacts).collect { syncResult ->
                            when (syncResult) {
                                is ResultState.InProgress -> Log.d(
                                    "ContactSync",
                                    "Upserting contacts..."
                                )

                                is ResultState.Success -> {
                                    repository.setPhoneTableLastUpdated(System.currentTimeMillis())
                                }

                                is ResultState.Failed -> Log.e(
                                    "ContactSync",
                                    "Error upserting contacts",
                                    syncResult.error
                                )
                            }
                        }
                    }
                }

                is ResultState.Failed -> Log.e(
                    "ContactSync",
                    "Error fetching contacts",
                    result.error
                )
            }
        }
    }
}