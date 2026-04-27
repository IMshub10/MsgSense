package com.summer.core.android.sms.processor

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.summer.core.android.sms.data.source.ISmsContentProvider
import com.summer.core.android.sms.data.mapper.SmsMapper
import com.summer.core.domain.model.FetchResult
import com.summer.core.data.local.dao.SmsDao
import com.summer.core.data.local.entities.SmsEntity
import com.summer.core.domain.model.SmsBatchResult
import com.summer.core.ml.model.SmsClassifierModel
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
 * Batches are processed concurrently for performance and fetched using ID-based pagination.
 */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class SmsBatchProcessor @Inject constructor(
    private val smsContentProvider: ISmsContentProvider,
    private val smsDao: SmsDao,
    private val smsClassifierModel: SmsClassifierModel,
    private val countryCodeProvider: CountryCodeProvider,
) {

    private val tag = "SmsBatchProcessor"
    private val classificationDispatcher = Dispatchers.Default.limitedParallelism(1)

    /**
     * Triggers the full SMS classification loop.
     *
     * The loop prioritizes:
     * 1. Newly arrived messages (based on _id > lastProcessedId)
     * 2. Remaining older messages (in descending order of _id) using concurrent offset-based paging.
     *
     * It emits progress updates as [FetchResult.Loading], and terminates once all known messages are processed.
     */
    fun processSmsInBatches(batchSize: Int, batchProcessingConcurrency: Int): Flow<FetchResult> = flow {
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
                    val fetchStartMs = System.currentTimeMillis()
                    val newCursor = smsContentProvider.getSmsCursorWithOffset(
                        offsetId = lastProcessedId,
                        limit = batchSize,
                        offset = 0,
                        isOrderAscending = true
                    )
                    val newMessages = newCursor?.use {
                        SmsMapper.mapCursorToSmsList(it, smsDao, countryCodeProvider.getMyCountryCode())
                    } ?: emptyList()
                    logBatchFetchDuration(
                        batchLabel = "new_messages",
                        messageCount = newMessages.size,
                        durationMs = System.currentTimeMillis() - fetchStartMs
                    )

                    // Recalculate SMS count in case new messages were inserted recently
                    totalSmsCount = smsContentProvider.getTotalSmsCount()

                    if (newMessages.isNotEmpty()) {
                        val classifiedNew = classifySmsBatch(newMessages)
                        insertClassifiedSms(classifiedNew, batchLabel = "new_messages")

                        Log.d(tag, "Inserted ${newMessages.size} SMS (afterId = $firstProcessedId)")

                        // Update pointer to newest processed _id
                        lastProcessedId = classifiedNew.maxOfOrNull { it.androidSmsId ?: lastProcessedId } ?: lastProcessedId

                        // Restart loop to check for even newer arrivals
                        continue
                    }
                }

                // Process remaining unclassified older messages using offset pagination
                val classifiedResults = processMultipleBatchesAfterId(
                    batchSize = batchSize,
                    baseId = firstProcessedId,
                    concurrency = batchProcessingConcurrency
                )

                val nonEmptyBatches = classifiedResults.filter { it.isNotEmpty() }
                if (nonEmptyBatches.isEmpty()) {
                    // No more old messages left to process
                    hasMoreData = false
                } else {
                    val allMessages = nonEmptyBatches.flatten()
                    insertClassifiedSms(allMessages, batchLabel = "older_messages")

                    processedCount += allMessages.size

                    // Update pointer to oldest processed _id
                    firstProcessedId = allMessages.minOfOrNull { it.androidSmsId ?: firstProcessedId } ?: firstProcessedId

                    Log.d(tag, "Inserted ${allMessages.size} SMS (beforeId = $firstProcessedId)")

                    // If fewer than expected batches had results, likely we're nearing the end
                    if (nonEmptyBatches.size < batchProcessingConcurrency) {
                        hasMoreData = false
                    }
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
        batchProcessingConcurrency: Int,
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
                        val fetchStartMs = System.currentTimeMillis()
                        val newCursor = smsContentProvider.getSmsCursorWithOffset(
                            offsetId = lastProcessedId,
                            limit = batchSize,
                            offset = 0,
                            isOrderAscending = true
                        )

                        val newMessages = newCursor?.use {
                            SmsMapper.mapCursorToSmsList(it, smsDao, countryCodeProvider.getMyCountryCode())
                        } ?: emptyList()
                        logBatchFetchDuration(
                            batchLabel = "new_messages",
                            messageCount = newMessages.size,
                            durationMs = System.currentTimeMillis() - fetchStartMs
                        )

                        totalSmsCount = smsContentProvider.getTotalSmsCount()

                        if (newMessages.isNotEmpty()) {
                            val classifiedNew = classifySmsBatch(newMessages)
                            insertClassifiedSms(classifiedNew, batchLabel = "new_messages")
                            lastProcessedId = classifiedNew.maxOfOrNull { it.androidSmsId ?: lastProcessedId } ?: lastProcessedId
                            continue
                        }
                    }

                    // Process older messages with offset paging
                    val classifiedResults = processMultipleBatchesAfterId(
                        batchSize = batchSize,
                        baseId = firstProcessedId,
                        concurrency = batchProcessingConcurrency
                    )

                    val nonEmptyBatches = classifiedResults.filter { it.isNotEmpty() }
                    if (nonEmptyBatches.isEmpty()) {
                        hasMoreData = false
                    } else {
                        val allMessages = nonEmptyBatches.flatten()
                        insertClassifiedSms(allMessages, batchLabel = "older_messages")
                        processedCount += allMessages.size
                        firstProcessedId = allMessages.minOfOrNull { it.androidSmsId ?: firstProcessedId } ?: firstProcessedId
                        Log.d(tag, "Inserted ${allMessages.size} SMS (beforeId = $firstProcessedId)")

                        if (nonEmptyBatches.size < batchProcessingConcurrency) {
                            hasMoreData = false
                        }
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
     * Spawns [concurrency] coroutines to fetch and classify SMS messages in parallel,
     * paging older messages below [baseId] using offset-based logic.
     */
    private suspend fun processMultipleBatchesAfterId(
        batchSize: Int,
        baseId: Int,
        concurrency: Int
    ): List<List<SmsEntity>> = coroutineScope {
        (0 until concurrency).map { index ->
            async(Dispatchers.Default) {
                processPreviousIdWithOffset(batchSize, baseId, index * batchSize)
            }
        }.awaitAll()
    }

    /**
     * Fetches a paged batch of SMS with _id < [baseId] and applies classification.
     * The offset simulates traditional pagination using MatrixCursor.
     */
    private suspend fun processPreviousIdWithOffset(
        batchSize: Int,
        baseId: Int,
        offset: Int
    ): List<SmsEntity> {
        val fetchStartMs = System.currentTimeMillis()
        val cursor = smsContentProvider.getSmsCursorWithOffset(
            offsetId = baseId,
            limit = batchSize,
            offset = offset,
            isOrderAscending = false
        )
        val fetchedMessages = cursor?.use {
            SmsMapper.mapCursorToSmsList(it, smsDao, countryCodeProvider.getMyCountryCode())
        } ?: emptyList()
        logBatchFetchDuration(
            batchLabel = "older_messages(offset=$offset)",
            messageCount = fetchedMessages.size,
            durationMs = System.currentTimeMillis() - fetchStartMs
        )
        return fetchedMessages.takeIf { it.isNotEmpty() }?.let {
            classifySmsBatch(it)
        } ?: emptyList()
    }

    /**
     * Classifies a batch of SMS messages using the [SmsClassifierModel].
     * Falls back to the original SMS entity on failure.
     */
    private suspend fun classifySmsBatch(smsBatch: List<SmsEntity>): List<SmsEntity> {
        if (smsBatch.isEmpty()) return emptyList()

        val classifyStartMs = System.currentTimeMillis()
        val classifiedBatch = withContext(classificationDispatcher) {
            smsBatch.map { sms ->
                try {
                    val classification =
                        smsClassifierModel.classifySms(sms.rawAddress, sms.body)
                    sms.copy(
                        importanceScore = classification.importanceScore,
                        smsClassificationTypeId = classification.smsClassificationTypeId,
                        confidenceScore = classification.confidenceScore
                    )
                } catch (e: Exception) {
                    Log.w(tag, "Error classifying SMS from ${sms.rawAddress}", e)
                    FirebaseCrashlytics.getInstance().recordException(e)
                    sms
                }
            }
        }

        val classifyDurationMs = System.currentTimeMillis() - classifyStartMs
        val perMessageAvgMs = classifyDurationMs.toDouble() / smsBatch.size
        Log.d(
            tag,
            "Classified batch size=${smsBatch.size} in ${classifyDurationMs}ms (avg=${"%.2f".format(perMessageAvgMs)}ms/msg)"
        )
        return classifiedBatch
    }

    /**
     * Inserts a list of classified SMS entities into the local Room database.
     * Logs and reports errors but does not crash the loop.
     */
    private suspend fun insertClassifiedSms(smsList: List<SmsEntity>, batchLabel: String) {
        val insertStartMs = System.currentTimeMillis()
        try {
            smsDao.insertAllSmsMessages(smsList)
            Log.d(
                tag,
                "Inserted batch=$batchLabel size=${smsList.size} in ${System.currentTimeMillis() - insertStartMs}ms"
            )
        } catch (e: Exception) {
            Log.e(tag, "DB insert failed", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    private fun logBatchFetchDuration(
        batchLabel: String,
        messageCount: Int,
        durationMs: Long
    ) {
        Log.d(tag, "Fetched batch=$batchLabel size=$messageCount in ${durationMs}ms")
    }
}
