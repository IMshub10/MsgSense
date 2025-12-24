package com.summer.core.domain.repository

import android.content.Context
import androidx.paging.PagingSource
import com.summer.core.android.sms.data.model.SmsInfoModel
import com.summer.core.android.sms.util.SmsStatus
import com.summer.core.data.local.entities.SmsClassificationTypeEntity
import com.summer.core.data.local.entities.SmsEntity
import com.summer.core.data.local.model.SearchSmsMessageQueryModel
import com.summer.core.domain.model.FetchResult
import com.summer.core.data.local.model.SmsMessageModel
import com.summer.core.domain.model.SearchSectionResult
import com.summer.core.domain.model.SmsBatchResult
import com.summer.core.ui.model.SmsImportanceType
import kotlinx.coroutines.flow.Flow

interface ISmsRepository {

    suspend fun fetchSmsMessagesFromDevice(): Flow<FetchResult>

    suspend fun fetchSmsMessagesFromDevice(onProgress: suspend (processed: Int, total: Int) -> Unit): SmsBatchResult

    fun setSmsProcessingStatusCompleted(isCompleted: Boolean)

    fun getPagedSmsMessagesPagedBySenderAddressId(
        senderAddressId: Long,
        smsImportanceType: SmsImportanceType
    ): PagingSource<Int, SmsMessageModel>

    suspend fun insertSms(context: Context, sms: SmsInfoModel, threadId: Long?): Long?

    suspend fun insertSms(smsEntity: SmsEntity): Long

    suspend fun markSmsAsReadBySenderId(context: Context, senderAddressId: Long): List<Long>

    suspend fun markSmsAsSentStatus(context: Context, smsId: Long, status: SmsStatus): Long?

    suspend fun markSmsAsDeliveredStatus(context: Context, smsId: Long, status: SmsStatus): Boolean

    suspend fun getOrInsertSenderId(senderAddress: String, defaultCountryCode: Int): Long

    suspend fun searchMessages(query: String): SearchSectionResult<SearchSmsMessageQueryModel>

    fun getSearchMessagesPagingSource(
        query: String,
        senderAddressId: Long
    ): PagingSource<Int, SearchSmsMessageQueryModel>

    suspend fun deleteSmsListFromDeviceAndLocal(
        context: Context,
        smsIds: List<Long>,
        androidSmsIds: List<Long>
    ): Int

    suspend fun getAllSmsClassificationTypes(): List<SmsClassificationTypeEntity>

    suspend fun updateSmsTypeImportance(id: Int, isImportant: Boolean)

    suspend fun isSenderBlocked(senderAddressId: Long): Boolean
}