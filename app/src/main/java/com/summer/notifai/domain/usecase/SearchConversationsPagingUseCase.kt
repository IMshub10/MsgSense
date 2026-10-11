package com.summer.notifai.domain.usecase

import androidx.paging.PagingSource
import com.summer.core.data.local.model.ContactMessageInfoModel
import com.summer.notifai.domain.repository.IContactRepository

import javax.inject.Inject

class SearchConversationsPagingUseCase @Inject constructor(private val repository: IContactRepository) {
    operator fun invoke(query: String): PagingSource<Int, ContactMessageInfoModel> {
        return repository.getSearchConversationsPagingSource(query)
    }
}