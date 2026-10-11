package com.summer.notifai.domain.usecase

import com.summer.core.data.local.model.ContactInfoInboxModel
import com.summer.notifai.domain.repository.IContactRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetContactInfoInboxModelUseCase @Inject constructor(
    private val contactRepository: IContactRepository
) {
    operator fun invoke(
        senderAddressId: Long,
        important:Int
    ): Flow<ContactInfoInboxModel?> {
        return contactRepository.getContactInfoBySenderAddressId(senderAddressId, important)
    }
}