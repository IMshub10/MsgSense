package com.summer.classifier

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.summer.core.classifier.SmsClassifier
import com.summer.core.data.local.entities.SmsEntity
import com.summer.core.domain.model.SmsBatchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Batch processor that:
 * 1. Always prioritizes newly received SMS (even during batch processing)
 * 2. Classifies unprocessed SMS using ML
 * 3. Persists results through [ClassifierRepository]
 *
 * Batches are fetched using ID-based pagination.
 */
@Singleton
class SmsBatchProcessor @Inject constructor(
    private val repository: ClassifierRepository,
    private val smsClassifier: SmsClassifier,
) {

    private val tag = "SmsBatchProcessor"

    /**
     * Runs the full SMS classification loop, prioritizing newly arrived messages over the
     * backlog of older ones, and reporting progress as each batch lands.
     */
    suspend fun processSmsInBatches(
        batchSize: Int,
        onProgress: suspend (processed: Int, total: Int) -> Unit
    ): SmsBatchResult {
        return withContext(Dispatchers.IO) {
            try {
                var newestClassifiedId = repository.newestClassifiedId()
                var oldestClassifiedId = repository.oldestClassifiedId()
                var processedCount = repository.classifiedCount()
                var totalSmsCount = repository.deviceSmsCount()
                var hasMoreData = true

                while (hasMoreData) {
                    onProgress(processedCount, totalSmsCount)

                    // Process newly arrived messages
                    val latestDeviceId = repository.newestDeviceId()
                    if (latestDeviceId > newestClassifiedId && newestClassifiedId != ClassifierRepository.NO_ID) {
                        val newMessages = repository.readNewer(newestClassifiedId, batchSize)

                        totalSmsCount = repository.deviceSmsCount()

                        if (newMessages.isNotEmpty()) {
                            val classifiedNew = classifySmsBatch(newMessages)
                            insertClassifiedSms(classifiedNew)
                            newestClassifiedId = classifiedNew.maxOfOrNull {
                                it.androidSmsId ?: newestClassifiedId
                            } ?: newestClassifiedId
                            continue
                        }
                    }

                    // Process the next batch of older messages
                    val olderMessages = repository.readOlder(oldestClassifiedId, batchSize)

                    if (olderMessages.isEmpty()) {
                        hasMoreData = false
                    } else {
                        val classifiedMessages = classifySmsBatch(olderMessages)
                        insertClassifiedSms(classifiedMessages)
                        processedCount += classifiedMessages.size
                        oldestClassifiedId = classifiedMessages.minOfOrNull {
                            it.androidSmsId ?: oldestClassifiedId
                        } ?: oldestClassifiedId
                        Log.d(tag, "Inserted ${classifiedMessages.size} SMS (beforeId = $oldestClassifiedId)")
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
            repository.saveClassified(smsList)
        } catch (e: Exception) {
            Log.e(tag, "DB insert failed", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }
}
