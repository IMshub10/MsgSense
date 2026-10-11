package com.summer.notifai.domain.usecase

import com.summer.notifai.domain.repository.ISmsRepository
import javax.inject.Inject

class IsSenderBlockedUseCase @Inject constructor(
    private val repository: ISmsRepository
) {
    suspend operator fun invoke(senderAddressId: Long): Boolean {
        return repository.isSenderBlocked(senderAddressId)
    }
}