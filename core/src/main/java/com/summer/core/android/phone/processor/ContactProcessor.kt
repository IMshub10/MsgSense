package com.summer.core.android.phone.processor

import android.content.Context
import android.provider.ContactsContract
import com.summer.core.android.phone.data.mapper.ContactMapper
import com.summer.core.android.phone.data.entity.ContactEntity
import com.summer.core.util.ResultState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class ContactProcessor(private val context: Context) {
    fun fetchContacts(lastUpdatedAt: Long = 0): Flow<ResultState<List<ContactEntity>>> = flow {
        emit(ResultState.InProgress)

        try {
            val contacts = mutableListOf<ContactEntity>()
            val contentResolver = context.contentResolver

            // Define selection to filter contacts updated after lastUpdatedAt
            val selection =
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_LAST_UPDATED_TIMESTAMP} > ?"
            val selectionArgs = arrayOf(lastUpdatedAt.toString())

            val cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone._ID,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_LAST_UPDATED_TIMESTAMP
                ),
                selection, selectionArgs,
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_LAST_UPDATED_TIMESTAMP} DESC" // Order by most recent updates
            )

            cursor?.use {
                while (it.moveToNext()) {
                    ContactMapper.mapCursorToContact(it)?.let { contact -> contacts.add(contact) }
                }
            }

            emit(ResultState.Success(contacts)) // Emit success with fetched contacts
        } catch (e: Exception) {
            emit(ResultState.Failed(e)) // Emit failure in case of an error
        }
    }.flowOn(Dispatchers.IO) // Ensures execution on IO thread
}