package com.summer.notifai.domain.usecase

import androidx.paging.PagingSource
import com.summer.core.data.local.entities.ContactEntity
import com.summer.notifai.domain.repository.IContactRepository
import javax.inject.Inject

class SearchContactsPagingUseCase @Inject constructor(private val repository: IContactRepository) {
    operator fun invoke(query: String): PagingSource<Int, ContactEntity> {
        return repository.getSearchContactsPagingSource(query)
    }
}