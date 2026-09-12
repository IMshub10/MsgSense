package com.summer.classifier

import com.summer.core.android.sms.data.mapper.SmsMapper
import com.summer.core.android.sms.data.source.ISmsContentProvider
import com.summer.core.data.local.dao.SmsDao
import com.summer.core.data.local.entities.SmsEntity
import com.summer.core.util.CountryCodeProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data layer for the classification slice: reads SMS off the device, tracks how far the
 * classification pass has got, and persists classified messages.
 *
 * Wraps the `:core` DAO and content provider so [SmsBatchProcessor] deals only in batches of
 * [SmsEntity] and never touches persistence directly.
 */
@Singleton
class ClassifierRepository @Inject constructor(
    private val smsContentProvider: ISmsContentProvider,
    private val smsDao: SmsDao,
    private val countryCodeProvider: CountryCodeProvider,
) {
    /** Newest `android_sms_id` already classified, or -1 when nothing has been processed. */
    suspend fun newestClassifiedId(): Int =
        smsDao.getLastInsertedSmsMessageByAndroidSmsId()?.androidSmsId ?: NO_ID

    /** Oldest `android_sms_id` already classified, or -1 when nothing has been processed. */
    suspend fun oldestClassifiedId(): Int =
        smsDao.getFirstInsertedSmsMessageByAndroidSmsId()?.androidSmsId ?: NO_ID

    suspend fun classifiedCount(): Int = smsDao.getTotalProcessedSmsCount()

    suspend fun deviceSmsCount(): Int = smsContentProvider.getTotalSmsCount()

    suspend fun newestDeviceId(): Int = smsContentProvider.getLastAndroidSmsId() ?: NO_ID

    /** Reads up to [limit] device messages newer than [afterId], oldest first. */
    suspend fun readNewer(afterId: Int, limit: Int): List<SmsEntity> =
        read(offsetId = afterId, limit = limit, isOrderAscending = true)

    /** Reads up to [limit] device messages older than [beforeId], newest first. */
    suspend fun readOlder(beforeId: Int, limit: Int): List<SmsEntity> =
        read(offsetId = beforeId, limit = limit, isOrderAscending = false)

    suspend fun saveClassified(smsList: List<SmsEntity>) {
        smsDao.insertAllSmsMessages(smsList)
    }

    private suspend fun read(offsetId: Int, limit: Int, isOrderAscending: Boolean): List<SmsEntity> {
        val cursor = smsContentProvider.getSmsCursorWithOffset(
            offsetId = offsetId,
            limit = limit,
            offset = 0,
            isOrderAscending = isOrderAscending,
        )
        return cursor?.use {
            SmsMapper.mapCursorToSmsList(it, smsDao, countryCodeProvider.getMyCountryCode())
        } ?: emptyList()
    }

    companion object {
        const val NO_ID = -1
    }
}
