package com.summer.classifier

import com.summer.core.android.device.util.DeviceTierEvaluator
import com.summer.classifier.SmsBatchProcessor
import com.summer.core.data.local.preference.PreferenceKey
import com.summer.core.data.local.preference.SharedPreferencesManager
import com.summer.core.domain.model.SmsBatchResult
import com.summer.core.ner.NerScheduler
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates a full device-SMS classification pass: pick device-tier batch settings, run the
 * batch classify+insert loop, and on success mark processing complete and kick the NER backfill.
 *
 * Extracted out of [com.summer.core.data.repository.SmsRepository] so the generic repository
 * (which stays in `:core`) no longer depends on the classifier pipeline or on `NerScheduler`.
 * This use case is the classification entry point the worker calls, and moves to `:classifier`
 * later in the split.
 */
@Singleton
class ClassifySmsUseCase @Inject constructor(
    private val smsBatchProcessor: SmsBatchProcessor,
    private val deviceTierEvaluator: DeviceTierEvaluator,
    private val sharedPreferencesManager: SharedPreferencesManager,
    private val nerScheduler: NerScheduler,
) {
    suspend operator fun invoke(
        onProgress: suspend (processed: Int, total: Int) -> Unit,
    ): SmsBatchResult {
        val batchSettings = deviceTierEvaluator.getRecommendedBatchSettings()
        val result = smsBatchProcessor.processSmsInBatches(
            batchSettings.first,
            onProgress,
        )
        if (result is SmsBatchResult.Success) {
            sharedPreferencesManager.saveData(PreferenceKey.SMS_PROCESSING_STATUS, true)
            nerScheduler.enqueueBackfill()
        }
        return result
    }
}
