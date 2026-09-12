package com.summer.notifai.domain.usecase

import androidx.paging.PagingSource
import com.summer.core.data.local.model.SmsMessageModel
import com.summer.notifai.domain.repository.ISmsRepository
import com.summer.core.ui.model.SmsImportanceType
import javax.inject.Inject

class GetSmsMessagesBySenderIdUseCase @Inject constructor(
    private val smsRepository: ISmsRepository
) {
    operator fun invoke(
        senderAddressId: Long,
        smsImportanceType: SmsImportanceType
    ): PagingSource<Int, SmsMessageModel> {
        return smsRepository.getPagedSmsMessagesPagedBySenderAddressId(
            senderAddressId,
            smsImportanceType
        )
    }
}