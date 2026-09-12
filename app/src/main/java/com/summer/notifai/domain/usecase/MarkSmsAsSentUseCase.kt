package com.summer.notifai.domain.usecase

import android.content.Context
import com.summer.core.android.sms.util.SmsStatus
import com.summer.notifai.domain.repository.ISmsRepository
import javax.inject.Inject

class MarkSmsAsSentUseCase @Inject constructor(
    private val smsRepository: ISmsRepository
) {
    /**
     * Inserts the SMS into system provider if not already present,
     * and updates its status as SENT.
     */
    suspend operator fun invoke(context: Context, smsId: Long, status: SmsStatus): Long? {
        return smsRepository.markSmsAsSentStatus(context, smsId, status)
    }
}