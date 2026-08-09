package com.summer.core.android.sms.processor

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.summer.core.android.sms.data.source.ISmsContentProvider
import com.summer.core.android.sms.data.mapper.SmsMapper
import com.summer.core.domain.model.FetchResult
import com.summer.core.data.local.dao.SmsDao
import com.summer.core.data.local.entities.SmsEntity
import com.summer.core.domain.model.SmsBatchResult
import com.summer.core.classifier.SmsClassifier
import com.summer.core.util.CountryCodeProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Batch processor that:
 * 1. Always prioritizes newly received SMS (even during batch processing)
 * 2. Classifies unprocessed SMS using ML
 * 3. Persists results into Room DB via [SmsDao]
 *
 * Batches are fetched using ID-based pagination.
 */
@Singleton
class SmsBatchProcessor @Inject constructor(
    private val smsContentProvider: ISmsContentProvider,
    private val smsDao: SmsDao,
    private val smsClassifier: SmsClassifier,
    private val countryCodeProvider: CountryCodeProvider,
) {

    private val tag = "SmsBatchProcessor"

    /**
     * Triggers the full SMS classification loop.
     *
     * The loop prioritizes:
     * 1. Newly arrived messages (based on _id > lastProcessedId)
     * 2. Remaining older messages (in descending order of _id) using offset-based paging.
     *
     * It emits progress updates as [FetchResult.Loading], and terminates once all known messages are processed.
     */
    fun processSmsInBatches(batchSize: Int): Flow<FetchResult> = flow {
        try {
            // Load current processing boundaries
            var lastProcessedId = smsDao.getLastInsertedSmsMessageByAndroidSmsId()?.androidSmsId ?: -1
            var firstProcessedId = smsDao.getFirstInsertedSmsMessageByAndroidSmsId()?.androidSmsId ?: -1
            var processedCount = smsDao.getTotalProcessedSmsCount()
            var totalSmsCount = smsContentProvider.getTotalSmsCount()
            var hasMoreData = true

            while (hasMoreData) {
                // Emit current loading progress
                emit(FetchResult.Loading(
                    processedCount = processedCount,
                    totalCount = totalSmsCount
                ))

                // Prioritize newly arrived messages above the last processed _id
                val latestDeviceId = smsContentProvider.getLastAndroidSmsId() ?: -1
                if (latestDeviceId > lastProcessedId && lastProcessedId != -1) {
                    val newCursor = smsContentProvider.getSmsCursorWithOffset(
                        offsetId = lastProcessedId,
                        limit = batchSize,
                        offset = 0,
                        isOrderAscending = true
                    )
                    val newMessages = newCursor?.use {
                        SmsMapper.mapCursorToSmsList(it, smsDao, countryCodeProvider.getMyCountryCode())
                    } ?: emptyList()

                    // Recalculate SMS count in case new messages were inserted recently
                    totalSmsCount = smsContentProvider.getTotalSmsCount()

                    if (newMessages.isNotEmpty()) {
                        val classifiedNew = classifySmsBatch(newMessages)
                        insertClassifiedSms(classifiedNew)

                        Log.d(tag, "Inserted ${newMessages.size} SMS (afterId = $firstProcessedId)")

                        // Update pointer to newest processed _id
                        lastProcessedId = classifiedNew.maxOfOrNull { it.androidSmsId ?: lastProcessedId } ?: lastProcessedId

                        // Restart loop to check for even newer arrivals
                        continue
                    }
                }

                // Process the next batch of remaining unclassified older messages
                val classifiedMessages = processPreviousId(
                    batchSize = batchSize,
                    baseId = firstProcessedId
                )

                if (classifiedMessages.isEmpty()) {
                    // No more old messages left to process
                    hasMoreData = false
                } else {
                    insertClassifiedSms(classifiedMessages)

                    processedCount += classifiedMessages.size

                    // Update pointer to oldest processed _id
                    firstProcessedId = classifiedMessages.minOfOrNull {
                        it.androidSmsId ?: firstProcessedId
                    } ?: firstProcessedId

                    Log.d(tag, "Inserted ${classifiedMessages.size} SMS (beforeId = $firstProcessedId)")
                }
            }

            emit(FetchResult.Success)
        } catch (e: Exception) {
            Log.e(tag, "Error processing SMS batches", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            emit(FetchResult.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun processSmsInBatches(
        batchSize: Int,
        onProgress: suspend (processed: Int, total: Int) -> Unit
    ): SmsBatchResult {
        return withContext(Dispatchers.IO) {
            try {
                var lastProcessedId = smsDao.getLastInsertedSmsMessageByAndroidSmsId()?.androidSmsId ?: -1
                var firstProcessedId = smsDao.getFirstInsertedSmsMessageByAndroidSmsId()?.androidSmsId ?: -1
                var processedCount = smsDao.getTotalProcessedSmsCount()
                var totalSmsCount = smsContentProvider.getTotalSmsCount()
                var hasMoreData = true

                while (hasMoreData) {
                    onProgress(processedCount, totalSmsCount)

                    // Process newly arrived messages
                    val latestDeviceId = smsContentProvider.getLastAndroidSmsId() ?: -1
                    if (latestDeviceId > lastProcessedId && lastProcessedId != -1) {
                        val newCursor = smsContentProvider.getSmsCursorWithOffset(
                            offsetId = lastProcessedId,
                            limit = batchSize,
                            offset = 0,
                            isOrderAscending = true
                        )

                        val newMessages = newCursor?.use {
                            SmsMapper.mapCursorToSmsList(it, smsDao, countryCodeProvider.getMyCountryCode())
                        } ?: emptyList()

                        totalSmsCount = smsContentProvider.getTotalSmsCount()

                        if (newMessages.isNotEmpty()) {
                            val classifiedNew = classifySmsBatch(newMessages)
                            insertClassifiedSms(classifiedNew)
                            lastProcessedId = classifiedNew.maxOfOrNull { it.androidSmsId ?: lastProcessedId } ?: lastProcessedId
                            continue
                        }
                    }

                    // Process the next batch of older messages
                    val classifiedMessages = processPreviousId(
                        batchSize = batchSize,
                        baseId = firstProcessedId
                    )

                    if (classifiedMessages.isEmpty()) {
                        hasMoreData = false
                    } else {
                        insertClassifiedSms(classifiedMessages)
                        processedCount += classifiedMessages.size
                        firstProcessedId = classifiedMessages.minOfOrNull {
                            it.androidSmsId ?: firstProcessedId
                        } ?: firstProcessedId
                        Log.d(tag, "Inserted ${classifiedMessages.size} SMS (beforeId = $firstProcessedId)")
                    }
                }
                SmsBatchResult.Success
            } catch (e: Exception) {
                Log.e(tag, "Error processing SMS batches", e)
                FirebaseCrashlytics.getInstance().recordException(e)
                SmsBatchResult.Failure(e)
            }
        }
    }

    /**
     * Fetches the next batch of SMS with _id < [baseId] and applies classification.
     */
    private suspend fun processPreviousId(
        batchSize: Int,
        baseId: Int
    ): List<SmsEntity> {
        val cursor = smsContentProvider.getSmsCursorWithOffset(
            offsetId = baseId,
            limit = batchSize,
            offset = 0,
            isOrderAscending = false
        )
        return cursor?.use {
            SmsMapper.mapCursorToSmsList(it, smsDao, countryCodeProvider.getMyCountryCode())
        }?.takeIf { it.isNotEmpty() }?.let {
            classifySmsBatch(it)
        } ?: emptyList()
    }

    /**
     * Classifies a batch of SMS messages using the [SmsClassifier].
     * Falls back to the original SMS entity on failure.
     */
    private suspend fun classifySmsBatch(smsBatch: List<SmsEntity>): List<SmsEntity> {
        return smsBatch.map { sms ->
            try {
                val classification = smsClassifier.classify(sms.rawAddress, sms.body)
                sms.copy(
                    importanceScore = classification.importanceScore,
                    smsClassificationTypeId = classification.smsClassificationTypeId,
                    confidenceScore = classification.confidenceScore
                )
            } catch (e: Exception) {
                Log.w(tag, "Error classifying SMS", e)
                FirebaseCrashlytics.getInstance().recordException(e)
                sms
            }
        }
    }

    /**
     * Inserts a list of classified SMS entities into the local Room database.
     * Logs and reports errors but does not crash the loop.
     */
    private suspend fun insertClassifiedSms(smsList: List<SmsEntity>) {
        try {
            smsDao.insertAllSmsMessages(smsList)
        } catch (e: Exception) {
            Log.e(tag, "DB insert failed", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }
}
