package com.summer.notifai.domain.repository

import androidx.paging.PagingSource
import com.summer.core.android.phone.data.entity.ContactEntity
import com.summer.core.data.local.model.ContactInfoInboxModel
import com.summer.core.data.local.model.ContactMessageInfoModel
import com.summer.core.domain.model.SearchSectionResult
import kotlinx.coroutines.flow.Flow

interface IContactRepository {
    suspend fun getAllContacts(): List<ContactEntity>

    fun getContactsWithFilter(query: String): PagingSource<Int, ContactEntity>

    suspend fun getContactById(contactId: Long): ContactEntity?

    suspend fun insertOrUpdateContact(contact: ContactEntity)

    fun setPhoneTableLastUpdated(timeInMillis: Long)

    fun getPhoneTableLastUpdated(): Long

    fun getPagedContactList(isImportant: Boolean): PagingSource<Int, ContactMessageInfoModel>

    fun getContactInfoBySenderAddressId(
        senderAddressId: Long,
        important: Int
    ): Flow<ContactInfoInboxModel?>

    suspend fun searchConversations(query: String): SearchSectionResult<ContactMessageInfoModel>

    fun getSearchConversationsPagingSource(query: String): PagingSource<Int, ContactMessageInfoModel>

    suspend fun searchContacts(query: String): SearchSectionResult<ContactEntity>

    fun getSearchContactsPagingSource(query: String): PagingSource<Int, ContactEntity>

    suspend fun blockSender(senderAddressId: Long)

    suspend fun unblockSender(senderAddressId: Long)

    fun getSearchBlockedSendersPagingSource(
        query: String
    ): PagingSource<Int, ContactInfoInboxModel>
}