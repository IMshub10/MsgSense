package com.summer.notifai.ui.inbox.smsMessages

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.summer.core.data.local.dao.SmsDao
import com.summer.core.data.local.model.SmsMessageModel
import com.summer.core.ui.model.SmsImportanceType
import com.summer.notifai.ui.datamodel.SmsInboxListItem
import com.summer.notifai.ui.datamodel.SmsMessageHeaderModel
import com.summer.notifai.ui.datamodel.mapper.SmsMessageMapper.toSmsMessageDataModel
import com.summer.core.util.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Loader for paginated SMS messages tied to a specific sender.
 *
 * Supports:
 * - Bidirectional paging (older/newer)
 * - Scroll-triggered loading
 * - Real-time updates via DB observation
 * - Message list state preservation
 *
 * Use [reinitialize] to start or restart the loader with a new filter or target message.
 */
class SmsMessageLoader(
    private val smsDao: SmsDao,
    private val senderAddressId: Long,
    private val scope: CoroutineScope,
    private val pageSize: Int
) {

    private val _messages = MutableStateFlow<List<SmsInboxListItem>>(emptyList())

    private val _selectedMessageIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedMessageIds: StateFlow<Set<Long>> get() = _selectedMessageIds

    /** Publicly exposed state of messages with mapped UI models. */
    val messages: StateFlow<List<SmsInboxListItem>> get() = _messages

    private var earliestMessage: SmsMessageModel? = null
    private var latestMessage: SmsMessageModel? = null

    private var isLoadingOlder = false
    private var isLoadingNewer = false
    private var hasMoreOlder = true
    private var hasMoreNewer = true
    private var hasStartedObservingDb = false

    private var _importanceType: SmsImportanceType? = null

    /** Current importance type used for filtering messages. */
    private val importanceType: SmsImportanceType
        get() = _importanceType ?: error("importanceType not initialized")

    private val messageMutex = Mutex()

    //Cache
    private val existingMessages = LinkedHashSet<SmsInboxListItem.Message>()
    private val existingMessageIds = HashSet<Long>() // optional if you're using a HashSet

    /**
     * (Internal) Loads initial set of messages around the given [targetSmsId].
     * Fetches `pageSize` before and after the target message.
     */
    private suspend fun loadInitial(targetSmsId: Long?) {
        clearMessages()

        val anchor = targetSmsId?.let { smsDao.getDateAndId(it) }
        val anchorDate = anchor?.date ?: Long.MAX_VALUE
        val anchorId = anchor?.id ?: Long.MAX_VALUE

        val before = smsDao.getMessagesBefore(
            senderAddressId,
            anchorDate,
            importanceType.value,
            anchorId,
            pageSize
        )
        val after = smsDao.getMessagesAfter(
            senderAddressId,
            anchorDate,
            importanceType.value,
            anchorId,
            pageSize
        )

        val combined = (before + after)
            .distinctBy { it.id }
            .sortedWith(compareByDescending<SmsMessageModel> { it.date }.thenByDescending { it.id })
        existingMessageIds.addAll(combined.map { it.id })
        earliestMessage = combined.lastOrNull()
        latestMessage = combined.firstOrNull()

        val mappedMessages = combined.map { SmsInboxListItem.Message(it.toSmsMessageDataModel()) }
        val processedMessages = buildListWithHeaders(
            mappedMessages,
            appendToTop = false
        )
        existingMessageIds.addAll(combined.map { it.id })
        existingMessages.addAll(mappedMessages)

        withContext(Dispatchers.Main) {
            _messages.value = processedMessages
        }
    }

    /**
     * Starts observing database changes to auto-load newer/older messages.
     * Only invoked once per instance lifecycle.
     */
    private fun observeForDbTriggers() {
        if (hasStartedObservingDb) return
        hasStartedObservingDb = true

        scope.launch(Dispatchers.IO) {
            launch {
                smsDao.observeMaxDateBySenderId(senderAddressId)
                    .distinctUntilChanged()
                    .collect {
                        loadNewerMessages(forceRefresh = true)
                    }
            }

            launch {
                smsDao.observeMinDateBySenderId(senderAddressId)
                    .distinctUntilChanged()
                    .collect {
                        loadOlderMessages(forceRefresh = true)
                    }
            }

            // Observe status updates (e.g., PENDING → COMPLETE/FAILED)
            launch {
                smsDao.observeMaxUpdatedAtBySenderId(senderAddressId)
                    .distinctUntilChanged()
                    .collect {
                        refreshMessageStatuses()
                    }
            }
        }
    }

    /**
     * Lightweight refresh for status updates only.
     * Fetches just the ID and status of recently updated messages,
     * then updates the cached items in-place using notifyChange().
     */
    private suspend fun refreshMessageStatuses() {
        val statusUpdates = smsDao.getRecentStatusUpdates(senderAddressId, limit = 10)
        if (statusUpdates.isEmpty()) return
        
        messageMutex.withLock {
            // Create a snapshot to avoid ConcurrentModificationException
            val snapshot = existingMessages.toList()
            
            for ((id, newStatus) in statusUpdates) {
                val existing = snapshot.find { it.data.id == id }
                if (existing != null && existing.data.status != newStatus) {
                    existing.data.status = newStatus
                    withContext(Dispatchers.Main) {
                        existing.data.notifyChange()
                    }
                }
            }
        }
    }

    /**
     * Loads older messages from the DB and appends them at the bottom.
     * Triggered via scroll or DB observation.
     */
    private fun loadOlderMessages(forceRefresh: Boolean = false) {
        if (!forceRefresh && !hasMoreOlder) return
        if (isLoadingOlder) return
        isLoadingOlder = true

        val anchor = earliestMessage ?: return

        scope.launch(Dispatchers.IO) {
            val older = smsDao.getMessagesBefore(
                senderAddressId,
                anchor.date,
                importanceType.value,
                anchor.id,
                pageSize
            )

            messageMutex.withLock {
                try {
                    if (older.isEmpty()) {
                        hasMoreOlder = false
                        return@withLock
                    }

                    earliestMessage = older.last()
                    val newUnique = older.filter { it.id !in existingMessageIds }
                        .map { SmsInboxListItem.Message(it.toSmsMessageDataModel()) }

                    if (newUnique.isNotEmpty()) {
                        existingMessages.addAll(newUnique)
                        existingMessageIds.addAll(newUnique.map { it.data.id })
                        val updated =
                            buildListWithHeaders(existingMessages.toList(), appendToTop = false)
                        withContext(Dispatchers.Main) {
                            _messages.value = updated
                        }
                    }
                } finally {
                    isLoadingOlder = false
                }
            }
        }
    }

    /**
     * Loads newer messages from the DB and prepends them to the top.
     * Triggered via scroll or DB observation.
     */
    private fun loadNewerMessages(forceRefresh: Boolean = false) {
        if (!forceRefresh && !hasMoreNewer) return
        if (isLoadingNewer) return
        isLoadingNewer = true

        val anchor = latestMessage ?: return

        scope.launch(Dispatchers.IO) {
            val newer = smsDao.getMessagesAfter(
                senderAddressId,
                anchor.date,
                importanceType.value,
                anchor.id,
                pageSize
            )

            messageMutex.withLock {
                try {
                    if (newer.isEmpty()) {
                        hasMoreNewer = false
                        return@withLock
                    }

                    latestMessage = newer.first()
                    val newUnique = newer.filter { it.id !in existingMessageIds }
                        .map { SmsInboxListItem.Message(it.toSmsMessageDataModel()) }

                    if (newUnique.isNotEmpty()) {
                        existingMessages.addAll(newUnique)
                        existingMessageIds.addAll(newUnique.map { it.data.id })
                        val updated =
                            buildListWithHeaders(existingMessages.toList(), appendToTop = true)
                        withContext(Dispatchers.Main) {
                            _messages.value = updated
                        }
                    }
                } finally {
                    isLoadingNewer = false
                }
            }
        }
    }

    /**
     * Clears current message state and resets internal markers.
     */
    private fun clearMessages() {
        _messages.value = emptyList()
        existingMessages.clear()
        existingMessageIds.clear()
        earliestMessage = null
        latestMessage = null
        hasMoreOlder = true
        hasMoreNewer = true
    }

    /**
     * Converts a list of messages to a header-separated inbox list.
     * Ensures correct header placement when using reverseLayout = true.
     *
     * @param newMessages List of new messages to integrate (assumed distinct).
     * @param appendToTop Whether this batch is being added to the top (newer messages).
     */
    private fun buildListWithHeaders(
        newMessages: List<SmsInboxListItem.Message>,
        appendToTop: Boolean
    ): List<SmsInboxListItem> {
        if (newMessages.isEmpty()) return emptyList()

        val result = mutableListOf<SmsInboxListItem>()
        val existing = _messages.value
        val referenceHeader = when {
            appendToTop -> (existing.firstOrNull { it is SmsInboxListItem.Header } as? SmsInboxListItem.Header)?.header?.label
            else -> (existing.lastOrNull { it is SmsInboxListItem.Header } as? SmsInboxListItem.Header)?.header?.label
        }

        val sorted = newMessages.sortedBy { it.data.dateInEpoch }

        var lastHeader: String? = referenceHeader

        for (msg in sorted) {
            val header = DateUtils.formatDayHeader(msg.data.dateInEpoch)
            if (header != lastHeader) {
                result.add(
                    SmsInboxListItem.Header(
                        SmsMessageHeaderModel(
                            header,
                            msg.data.dateInEpoch
                        )
                    )
                )
                lastHeader = header
            }
            result.add(msg)
        }

        return result.reversed()
    }

    /**
     * Resets and restarts message loading with the given filter and optional anchor message.
     *
     * @param newImportanceType Type of messages to include.
     * @param newTargetId If set, loads around this message ID.
     */
    suspend fun reinitialize(
        newImportanceType: SmsImportanceType = SmsImportanceType.ALL,
        newTargetId: Long? = null
    ) {
        _importanceType = newImportanceType
        messageMutex.withLock {
            clearMessages()
            loadInitial(newTargetId)
            observeForDbTriggers()
        }
    }

    /**
     * Attaches paging triggers to a RecyclerView for bidirectional paging.
     */
    fun attachScrollListener(recyclerView: RecyclerView, layoutManager: LinearLayoutManager) {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)
                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                val totalCount = layoutManager.itemCount

                val threshold = pageSize / 4

                if (firstVisible <= (threshold)) loadNewerMessages()
                if (lastVisible >= totalCount - (threshold)) loadOlderMessages()
            }
        })
    }

    /**
     * Toggles the selection state of a message with the given [messageId].
     * If the message is currently selected, it will be deselected, and vice versa.
     *
     * Automatically updates the [selectedMessageIds] state and notifies the message model to trigger UI updates.
     *
     * @param messageId The ID of the message to toggle selection for.
     */
    fun toggleMessageSelection(messageId: Long) {
        val current = _selectedMessageIds.value.toMutableSet()
        val isSelected = if (current.contains(messageId)) {
            current.remove(messageId)
            false
        } else {
            current.add(messageId)
            true
        }
        _selectedMessageIds.value = current
        updateSingleMessageSelection(messageId, isSelected)
    }

    /**
     * Clears all currently selected messages.
     * Resets all message models' `isSelected` flags to false and notifies changes to trigger UI refresh.
     */
    fun clearAllSelections() {
        _selectedMessageIds.value = emptySet()
        existingMessages.forEach {
            it.data.isSelected = false
            it.data.notifyChange()
        }
    }

    /**
     * Updates the `isSelected` state of a specific message by ID and triggers `notifyChange()` to refresh the UI.
     *
     * This is used internally after a toggle or clear operation to perform a targeted update.
     *
     * @param messageId The ID of the message to update.
     * @param isSelected Whether the message should be marked as selected.
     */
    private fun updateSingleMessageSelection(messageId: Long, isSelected: Boolean) {
        val found = existingMessages.find { it.data.id == messageId }
        if (found != null) {
            found.data.isSelected = isSelected
            found.data.notifyChange()
        }
    }

    /**
     * Factory for injecting [SmsMessageLoader] via Hilt or manual instantiation.
     */
    interface Factory {
        fun create(
            senderAddressId: Long,
            pageSize: Int,
            scope: CoroutineScope
        ): SmsMessageLoader
    }
}
