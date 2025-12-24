package com.summer.core.data.repository

import android.content.ContentValues
import android.content.Context
import android.provider.Telephony
import android.util.Log
import androidx.collection.LruCache
import androidx.paging.PagingSource
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.summer.core.R
import com.summer.core.android.device.util.DeviceTierEvaluator
import com.summer.core.android.sms.constants.Constants.SEARCH_SECTION_MAX_COUNT
import com.summer.core.android.sms.constants.SMSColumnNames
import com.summer.core.android.sms.data.mapper.SmsMapper
import com.summer.core.android.sms.data.model.SmsInfoModel
import com.summer.core.android.sms.processor.SmsBatchProcessor
import com.summer.core.android.sms.util.SmsStatus
import com.summer.core.data.local.dao.SmsDao
import com.summer.core.data.local.entities.SmsClassificationTypeEntity
import com.summer.core.data.local.entities.SmsEntity
import com.summer.core.data.local.model.SearchSmsMessageQueryModel
import com.summer.core.data.local.model.SmsMessageModel
import com.summer.core.data.local.preference.PreferenceKey
import com.summer.core.data.local.preference.SharedPreferencesManager
import com.summer.core.domain.model.FetchResult
import com.summer.core.domain.model.SearchSectionHeader
import com.summer.core.domain.model.SearchSectionId
import com.summer.core.domain.model.SearchSectionResult
import com.summer.core.domain.model.SmsBatchResult
import com.summer.core.domain.repository.ISmsRepository
import com.summer.core.ui.model.SmsImportanceType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsRepository @Inject constructor(
    private val smsDao: SmsDao,
    private val smsBatchProcessor: SmsBatchProcessor,
    private val sharedPreferencesManager: SharedPreferencesManager,
    private val deviceTierEvaluator: DeviceTierEvaluator
) : ISmsRepository {

    override suspend fun fetchSmsMessagesFromDevice(): Flow<FetchResult> {
        val batchSettings = deviceTierEvaluator.getRecommendedBatchSettings()
        return smsBatchProcessor.processSmsInBatches(
            batchSize = batchSettings.first,
            batchSettings.second
        )
    }

    override suspend fun fetchSmsMessagesFromDevice(onProgress: suspend (Int, Int) -> Unit): SmsBatchResult {
        val batchSettings = deviceTierEvaluator.getRecommendedBatchSettings()
        return smsBatchProcessor.processSmsInBatches(
            batchSettings.first,
            batchSettings.second
        ) { processedCount, totalCount ->
            onProgress(processedCount, totalCount)
        }
    }

    override fun setSmsProcessingStatusCompleted(isCompleted: Boolean) {
        sharedPreferencesManager.saveData(PreferenceKey.SMS_PROCESSING_STATUS, isCompleted)
    }

    override fun getPagedSmsMessagesPagedBySenderAddressId(
        senderAddressId: Long,
        smsImportanceType: SmsImportanceType
    ): PagingSource<Int, SmsMessageModel> {
        return smsDao.getPagedSmsMessagesPagedBySenderAddressId(
            senderAddressId,
            smsImportanceType.value
        )
    }

    override suspend fun insertSms(context: Context, sms: SmsInfoModel, threadId: Long?): Long? {
        val values = SmsMapper.smsInfoModelToContentValues(sms, threadId)
        return try {
            val uri = context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
            uri?.lastPathSegment?.toLongOrNull()
        } catch (e: Exception) {
            Log.e("SmsRepository", "Failed to insert SMS", e)
            null
        }
    }

    override suspend fun insertSms(smsEntity: SmsEntity): Long {
        return smsDao.insertSmsMessage(smsEntity)
    }

    override suspend fun markSmsAsReadBySenderId(
        context: Context,
        senderAddressId: Long
    ): List<Long> {
        val smsIds = smsDao.getUnreadAndroidSmsIdsBySenderAddressId(senderAddressId)

        // Always update in Room (internal DB)
        smsDao.markSmsAsReadBySenderAddressId(senderAddressId)

        if (smsIds.isEmpty()) return emptyList()

        try {
            val shouldUpdateSystem = sharedPreferencesManager.getDataBoolean(
                PreferenceKey.SAVE_DATA_IN_PUBLIC_DB,
                true
            )

            if (shouldUpdateSystem) {
                val values = ContentValues().apply {
                    put(Telephony.Sms.READ, 1)
                }

                val placeholders = smsIds.joinToString(",") { "?" }
                val whereClause = "${SMSColumnNames.COLUMN_ID} IN ($placeholders)"
                val whereArgs = smsIds.map { it.toString() }.toTypedArray()

                context.contentResolver.update(
                    Telephony.Sms.CONTENT_URI,
                    values,
                    whereClause,
                    whereArgs
                )
            }

        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
            Log.e("SmsRepository", "Failed to mark SMS as read in system provider", e)
        }

        return smsIds
    }

    override suspend fun markSmsAsSentStatus(
        context: Context,
        smsId: Long,
        status: SmsStatus
    ): Long? {
        return try {
            val smsEntity = smsDao.getSmsEntityById(smsId) ?: return null

            // Already inserted → skip system insert
            if (smsEntity.androidSmsId != null) {
                smsDao.updateSmsStatusById(smsId, status.code, System.currentTimeMillis())
                return smsEntity.androidSmsId.toLong()
            }

            //  Check user setting before inserting into system SMS provider
            val shouldInsertInSystem = sharedPreferencesManager.getDataBoolean(
                PreferenceKey.SAVE_DATA_IN_PUBLIC_DB,
                true
            )

            var androidSmsId: Long? = null

            if (shouldInsertInSystem) {
                val values = SmsMapper.smsEntityToContentValuesForSent(smsEntity)
                val uri = context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
                androidSmsId = uri?.lastPathSegment?.toLongOrNull()

                if (androidSmsId != null) {
                    smsDao.updateAndroidSmsId(smsEntity.id, androidSmsId.toInt())
                }
            }

            smsDao.updateSmsStatusById(smsEntity.id, status.code, System.currentTimeMillis())
            androidSmsId

        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
            Log.e("SmsRepository", "Failed to insert sent SMS into system provider", e)
            null
        }
    }

    override suspend fun markSmsAsDeliveredStatus(
        context: Context,
        smsId: Long,
        status: SmsStatus
    ): Boolean {
        return try {
            val smsEntity = smsDao.getSmsEntityById(smsId) ?: return false

            // Always update Room
            smsDao.updateSmsStatusById(smsId, status.code, System.currentTimeMillis())

            val shouldInsertInSystem = sharedPreferencesManager.getDataBoolean(
                PreferenceKey.SAVE_DATA_IN_PUBLIC_DB,
                true // default is to insert in public DB unless toggled off
            )

            val androidSmsId = smsEntity.androidSmsId

            if (!shouldInsertInSystem) {
                return true // User prefers local-only storage; Room is already updated
            }

            if (androidSmsId != null) {
                // Already in system SMS DB → update status only
                val values = ContentValues().apply {
                    put(Telephony.Sms.STATUS, status.code)
                }

                val updatedRows = context.contentResolver.update(
                    Telephony.Sms.CONTENT_URI,
                    values,
                    "${Telephony.Sms._ID} = ?",
                    arrayOf(androidSmsId.toString())
                )
                return updatedRows > 0
            } else {
                // Not in system yet → insert + map ID
                val values = SmsMapper.smsEntityToContentValuesForSent(smsEntity)
                val uri = context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
                val insertedId = uri?.lastPathSegment?.toLongOrNull()

                if (insertedId != null) {
                    smsDao.updateAndroidSmsId(smsEntity.id, insertedId.toInt())
                    return true
                }
            }

            false
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
            Log.e("SmsRepository", "Failed to mark SMS as delivered (id=$smsId)", e)
            false
        }
    }

    override suspend fun getOrInsertSenderId(senderAddress: String, defaultCountryCode: Int): Long {
        return smsDao.getOrInsertSenderId(senderAddress, defaultCountryCode)
    }

    override suspend fun searchMessages(query: String): SearchSectionResult<SearchSmsMessageQueryModel> {
        val count = smsDao.getMessagesMatchCount(query)
        val items = if (count > 0) smsDao.searchMessages(
            query,
            limit = SEARCH_SECTION_MAX_COUNT
        ) else emptyList()
        return SearchSectionResult(
            header = SearchSectionHeader(
                id = SearchSectionId.MESSAGES,
                titleResId = R.string.search_section_messages,
                count = count
            ),
            items = items
        )
    }

    override fun getSearchMessagesPagingSource(
        query: String,
        senderAddressId: Long
    ): PagingSource<Int, SearchSmsMessageQueryModel> {
        return smsDao.getSearchMessagesPagingSource(query, senderAddressId)
    }

    override suspend fun deleteSmsListFromDeviceAndLocal(
        context: Context,
        smsIds: List<Long>,
        androidSmsIds: List<Long>
    ): Int {
        if (smsIds.isEmpty()) return 0

        var deletedFromProvider = 0

        // Delete from Content Provider (system SMS)
        if (androidSmsIds.isNotEmpty()) {
            try {
                val whereClause = "${Telephony.Sms._ID} IN (${androidSmsIds.joinToString(",")})"
                deletedFromProvider = context.contentResolver.delete(
                    Telephony.Sms.CONTENT_URI,
                    whereClause,
                    null
                )
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().recordException(e)
                Log.e("SmsRepository", "Failed to delete from content provider", e)
            }
        }

        // Delete from Room database
        smsDao.deleteSmsMessagesByIds(smsIds)

        return deletedFromProvider
    }

    override suspend fun getAllSmsClassificationTypes(): List<SmsClassificationTypeEntity> {
        return smsDao.getAllSmsClassificationTypes()
    }

    override suspend fun updateSmsTypeImportance(id: Int, isImportant: Boolean) {
        smsDao.updateSmsTypeImportance(id, isImportant)
    }

    override suspend fun isSenderBlocked(senderAddressId: Long): Boolean {
        return smsDao.isSenderBlocked(senderAddressId)
    }
}