package com.summer.core.domain.usecase

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.summer.core.data.local.model.ContactInfoInboxModel
import com.summer.core.domain.repository.IContactRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchBlockedSendersUseCase @Inject constructor(
    private val repository: IContactRepository
) {
    operator fun invoke(query: String): Flow<PagingData<ContactInfoInboxModel>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                repository.getSearchBlockedSendersPagingSource(query.trim().lowercase())
            }
        ).flow
    }
}