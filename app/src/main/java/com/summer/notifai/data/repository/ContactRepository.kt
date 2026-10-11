package com.summer.notifai.data.repository

import androidx.paging.PagingSource
import com.summer.core.R
import com.summer.core.data.local.dao.ContactDao
import com.summer.core.data.local.entities.ContactEntity
import com.summer.core.android.sms.constants.Constants.SEARCH_SECTION_MAX_COUNT
import com.summer.notifai.domain.repository.IContactRepository
import com.summer.core.data.local.model.ContactInfoInboxModel
import com.summer.core.data.local.model.ContactMessageInfoModel
import com.summer.core.data.local.preference.PreferenceKey
import com.summer.core.data.local.preference.SharedPreferencesManager
import com.summer.core.domain.model.SearchSectionHeader
import com.summer.core.domain.model.SearchSectionId
import com.summer.core.domain.model.SearchSectionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepository @Inject constructor(
    private val contactDao: ContactDao,
    private val sharedPreferencesManager: SharedPreferencesManager
) : IContactRepository {

    override suspend fun getAllContacts(): List<ContactEntity> = withContext(Dispatchers.IO) {
        contactDao.getAllContacts()
    }

    override fun getContactsWithFilter(query: String): PagingSource<Int, ContactEntity> {
        return contactDao.getContactsWithFilter(query)
    }

    override suspend fun getContactById(contactId: Long): ContactEntity? =
        withContext(Dispatchers.IO) {
            contactDao.getContactById(contactId)
        }

    override suspend fun insertOrUpdateContact(contact: ContactEntity) =
        withContext(Dispatchers.IO) {
            contactDao.insertOrUpdateContact(contact)
        }


    override fun setPhoneTableLastUpdated(timeInMillis: Long) {
        sharedPreferencesManager.saveData(PreferenceKey.PHONE_TABLE_LAST_UPDATED, timeInMillis)
    }

    override fun getPhoneTableLastUpdated(): Long {
        return sharedPreferencesManager.getDataLong(PreferenceKey.PHONE_TABLE_LAST_UPDATED, 0L)
    }

    override fun getPagedContactList(
        isImportant: Boolean,
    ): PagingSource<Int, ContactMessageInfoModel> {
        return contactDao.getContactListByImportancePaged(isImportant)
    }

    override fun getContactInfoBySenderAddressId(
        senderAddressId: Long,
        important: Int //TODO(remove if not required)
    ): Flow<ContactInfoInboxModel?> {
        return contactDao.getContactInfoBySenderAddressId(
            senderAddressId = senderAddressId
        )
    }

    override suspend fun searchConversations(query: String): SearchSectionResult<ContactMessageInfoModel> {
        val count = contactDao.getConversationsMatchCount(query)
        val items = if (count > 0) contactDao.searchConversations(
            query,
            SEARCH_SECTION_MAX_COUNT
        ) else emptyList()
        return SearchSectionResult(
            header = SearchSectionHeader(
                id = SearchSectionId.CONVERSATIONS,
                titleResId = R.string.search_section_conversations,
                count = count
            ),
            items = items
        )
    }

    override fun getSearchConversationsPagingSource(query: String): PagingSource<Int, ContactMessageInfoModel> {
        return contactDao.getSearchConversationsPagingSource(query)
    }

    override suspend fun searchContacts(query: String): SearchSectionResult<ContactEntity> {
        val count = contactDao.getContactsCount(query)
        val items = if (count > 0) contactDao.searchContacts(
            query,
            SEARCH_SECTION_MAX_COUNT
        ) else emptyList()
        return SearchSectionResult(
            header = SearchSectionHeader(
                id = SearchSectionId.CONTACTS,
                titleResId = R.string.search_section_contacts,
                count = count
            ),
            items = items
        )
    }

    override fun getSearchContactsPagingSource(query: String): PagingSource<Int, ContactEntity> {
        return contactDao.getSearchContactsPagingSource(query)
    }

    override suspend fun blockSender(senderAddressId: Long) {
        contactDao.blockSender(senderAddressId)
    }

    override suspend fun unblockSender(senderAddressId: Long) {
        contactDao.unblockSender(senderAddressId)
    }

    override fun getSearchBlockedSendersPagingSource(query: String): PagingSource<Int, ContactInfoInboxModel> {
        return contactDao.getSearchBlockedSendersPagingSource(query)
    }
}