package com.summer.notifai.domain.usecase

import com.summer.core.android.phone.data.entity.ContactEntity
import com.summer.core.domain.model.SearchSectionResult
import com.summer.notifai.domain.repository.IContactRepository
import javax.inject.Inject

class SearchContactsUseCase @Inject constructor(private val repo: IContactRepository) {
    suspend operator fun invoke(query: String): SearchSectionResult<ContactEntity> {
        return repo.searchContacts(query)
    }
}