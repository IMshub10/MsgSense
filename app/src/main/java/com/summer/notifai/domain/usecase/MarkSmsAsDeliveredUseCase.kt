package com.summer.notifai.domain.usecase

import android.content.Context
import com.summer.core.android.sms.util.SmsStatus
import com.summer.notifai.domain.repository.ISmsRepository
import javax.inject.Inject

class MarkSmsAsDeliveredUseCase @Inject constructor(
    private val smsRepository: ISmsRepository
) {
    /**
     * Updates the existing SMS in the system provider and Room
     * with delivery status (DELIVERED).
     */
    suspend operator fun invoke(context: Context, smsId: Long, status: SmsStatus): Boolean {
        return smsRepository.markSmsAsDeliveredStatus(context, smsId, status)
    }
}