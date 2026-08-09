package com.summer.notifai.domain.usecase

import com.summer.core.data.local.dao.ContactDao
import com.summer.core.android.phone.data.entity.ContactEntity
import com.summer.core.data.local.preference.PreferenceKey
import com.summer.core.data.local.preference.SharedPreferencesManager
import com.summer.core.util.ResultState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class SyncContactsUseCase @Inject constructor(
    private val contactDao: ContactDao,
    private val sharedPreferencesManager: SharedPreferencesManager
) {
    fun execute(newContacts: List<ContactEntity>): Flow<ResultState<Int>> = flow {
        emit(ResultState.InProgress)
        try {
            sharedPreferencesManager.saveData(PreferenceKey.PHONE_TABLE_LAST_UPDATED, System.currentTimeMillis())
            val existingContacts = contactDao.getAllContacts() // Fetch existing contacts from DB
            val existingNumbers = existingContacts.map { it.phoneNumber }.toSet()

            val contactsToInsert = newContacts.filter { it.phoneNumber !in existingNumbers }

            if (contactsToInsert.isNotEmpty()) {
                contactsToInsert.forEach {
                    contactDao.insertOrUpdateContact(it)
                }
            }
            emit(ResultState.Success(contactsToInsert.size)) // Success with number of inserted contacts
        } catch (e: Exception) {
            emit(ResultState.Failed(e)) // Emit error state
        }
    }
}