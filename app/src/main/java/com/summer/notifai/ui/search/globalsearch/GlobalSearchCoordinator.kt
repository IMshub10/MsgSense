package com.summer.notifai.ui.search.globalsearch

import com.summer.notifai.domain.usecase.SearchContactsUseCase
import com.summer.notifai.domain.usecase.SearchConversationsUseCase
import com.summer.notifai.domain.usecase.SearchMessagesUseCase
import com.summer.notifai.ui.datamodel.GlobalSearchListItem
import com.summer.notifai.ui.datamodel.mapper.ContactInfoMapper.toContactMessageInfoDataModel
import com.summer.notifai.ui.datamodel.mapper.NewContactMapper.toNewContactDataModel
import com.summer.notifai.ui.datamodel.mapper.SearchSmsMessageMapper.toSearchSmsMessageDataModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import javax.inject.Inject

class GlobalSearchCoordinator @Inject constructor(
    private val messagesUseCase: SearchMessagesUseCase,
    private val conversationsUseCase: SearchConversationsUseCase,
    private val contactsUseCase: SearchContactsUseCase
) {

    suspend fun performGlobalSearch(
        query: String,
        scope: CoroutineScope
    ): List<GlobalSearchListItem> {
        val queryString = query.lowercase()

        val messagesDeferred = scope.async {
            messagesUseCase(queryString).let {
                it.header to it.items.map { m ->
                    GlobalSearchListItem.SmsItem(m.toSearchSmsMessageDataModel())
                }
            }
        }

        val conversationsDeferred = scope.async {
            conversationsUseCase(queryString).let {
                it.header to it.items.map { c ->
                    GlobalSearchListItem.ConversationItem(c.toContactMessageInfoDataModel())
                }
            }
        }

        val contactsDeferred = scope.async {
            contactsUseCase(queryString).let {
                it.header to it.items.map { c ->
                    GlobalSearchListItem.ContactItem(c.toNewContactDataModel())
                }
            }
        }

        return awaitAll(messagesDeferred, conversationsDeferred, contactsDeferred)
            .flatMap { (header, items) ->
                if (items.isEmpty()) emptyList()
                else listOf(
                    GlobalSearchListItem.SectionHeader(
                        id = header.id,
                        titleResId = header.titleResId,
                        count = header.count
                    )
                ) + items
            }
    }
}