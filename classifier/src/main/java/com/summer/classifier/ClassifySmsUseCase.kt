package com.summer.classifier

import com.summer.core.android.device.util.DeviceTierEvaluator
import com.summer.core.data.local.preference.PreferenceKey
import com.summer.core.data.local.preference.SharedPreferencesManager
import com.summer.core.domain.model.SmsBatchResult
import com.summer.core.ner.NerScheduler
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates a full device-SMS classification pass: pick device-tier batch settings, run the
 * batch classify+insert loop, and on success mark processing complete and kick the NER backfill.
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
