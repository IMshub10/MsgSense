package com.summer.notifai.domain.usecase

import android.content.Context
import com.summer.notifai.domain.repository.ISmsRepository
import javax.inject.Inject


class MarkSmsAsReadForSenderUseCase @Inject constructor(
    private val smsRepository: ISmsRepository
) {
    suspend operator fun invoke(
        context: Context,
        senderAddressId: Long,
    ) : List<Long>{
        return smsRepository.markSmsAsReadBySenderId(
            context = context,
            senderAddressId = senderAddressId
        )
    }
}