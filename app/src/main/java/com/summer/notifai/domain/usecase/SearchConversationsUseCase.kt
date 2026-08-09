package com.summer.notifai.domain.usecase

import com.summer.core.data.local.model.ContactMessageInfoModel
import com.summer.core.domain.model.SearchSectionResult
import com.summer.notifai.domain.repository.IContactRepository
import javax.inject.Inject

class SearchConversationsUseCase @Inject constructor(private val repository: (IContactRepository)) {
    suspend operator fun invoke(query: String): SearchSectionResult<ContactMessageInfoModel> {
        return repository.searchConversations(query)
    }
}