package com.summer.notifai.domain.usecase

import com.summer.notifai.domain.repository.IContactRepository
import javax.inject.Inject

class UnblockSenderUseCase @Inject constructor(
    private val repository: IContactRepository
) {
    suspend operator fun invoke(senderAddressId: Long) {
        repository.unblockSender(senderAddressId)
    }
}