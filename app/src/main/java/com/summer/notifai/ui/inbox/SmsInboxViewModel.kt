package com.summer.notifai.ui.inbox

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.summer.core.android.sms.constants.Constants
import com.summer.core.android.sms.constants.Constants.SMS_LIST_PAGE_SIZE
import com.summer.core.data.local.entities.SenderType
import com.summer.core.data.local.model.ContactInfoInboxModel
import com.summer.core.domain.usecase.BlockSenderUseCase
import com.summer.core.domain.usecase.DeleteSmsByIdsUseCase
import com.summer.core.domain.usecase.GetContactInfoInboxModelUseCase
import com.summer.core.domain.usecase.MarkSmsAsReadForSenderUseCase
import com.summer.core.domain.usecase.SendSmsUseCase
import com.summer.core.ui.model.SmsImportanceType
import com.summer.core.util.isValidPhoneNumber
import com.summer.notifai.ui.datamodel.SmsInboxListItem
import com.summer.notifai.ui.datamodel.SmsMessageDataModel
import com.summer.notifai.ui.inbox.smsMessages.SmsMessageLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SmsInboxViewModel @Inject constructor(
    private val sendSmsUseCase: SendSmsUseCase,
    private val getContactInfoInboxModelUseCase: GetContactInfoInboxModelUseCase,
    private val markSmsAsReadForSenderUseCase: MarkSmsAsReadForSenderUseCase,
    private val deleteSmsByIdsUseCase: DeleteSmsByIdsUseCase,
    private val blockSenderUseCase: BlockSenderUseCase,
    private val smsMessageLoaderFactory: SmsMessageLoader.Factory
) : ViewModel() {

    var senderAddressId = 0L
    var isListInitScrollCalled = false
    var targetSmsId: Long? = null
    private var smsImportanceType: SmsImportanceType = SmsImportanceType.ALL

    val messageText = MutableLiveData("")

    val isSendEnabled: LiveData<Boolean> = messageText.map { message ->
        val parts = SmsManager.getDefault().divideMessage(message ?: "")
        !message.isNullOrBlank() && parts.size <= Constants.SMS_PART_LIMIT
    }

    val isAtBottom = MutableStateFlow(true)
    val isRecyclerViewScrolling = MutableStateFlow(false)

    val isScrollDownButtonVisible =
        combine(isAtBottom, isRecyclerViewScrolling) { atBottom, scrolling ->
            scrolling && !atBottom
        }.asLiveData()

    private val _contactInfoModel = MutableLiveData<ContactInfoInboxModel?>(null)
    val contactInfoModel: LiveData<ContactInfoInboxModel?> = _contactInfoModel

    private val _messageLoader = MutableStateFlow<SmsMessageLoader?>(null)
    val messageLoader: StateFlow<SmsMessageLoader?> get() = _messageLoader.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedMessages: StateFlow<List<SmsMessageDataModel>> =
        combine(
            _messageLoader,
            _messageLoader.flatMapLatest { it?.selectedMessageIds ?: MutableStateFlow(emptySet()) },
            _messageLoader.flatMapLatest { it?.messages ?: MutableStateFlow(emptyList()) }
        ) { _, selectedIds, items ->
            items.filterIsInstance<SmsInboxListItem.Message>()
                .map { it.data }
                .filter { selectedIds.contains(it.id) }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val messageSelectedCount: LiveData<Int> = selectedMessages
        .map { it.size }
        .asLiveData()

    val isSelectedSectionVisible: LiveData<Boolean> = selectedMessages
        .map { it.isNotEmpty() }
        .asLiveData()

    val isSendSectionVisible = MediatorLiveData<Boolean>().apply {
        fun update() {
            val contactVisible = contactInfoModel.value?.senderType == SenderType.CONTACT
            val hasSelection = selectedMessages.value.isNotEmpty()
            value = contactVisible && !hasSelection
        }
        addSource(contactInfoModel) { update() }
        addSource(selectedMessages.asLiveData()) { update() }
    }

    private val _highlightedMessageId = MutableStateFlow<Long?>(null)
    val highlightedMessageId: StateFlow<Long?> = _highlightedMessageId

    private val flashHandler = Handler(Looper.getMainLooper())
    private var flashResetRunnable: Runnable? = null

    fun flashMessage(id: Long, resetCallback: () -> Unit) {
        // Cancel any pending reset from previous flash
        cancelFlash()
        
        _highlightedMessageId.value = id
        flashResetRunnable = Runnable {
            _highlightedMessageId.value = null
            resetCallback()
        }
        flashHandler.postDelayed(flashResetRunnable!!, 1500L)
    }

    fun cancelFlash() {
        flashResetRunnable?.let { flashHandler.removeCallbacks(it) }
        flashResetRunnable = null
    }

    override fun onCleared() {
        super.onCleared()
        cancelFlash()
    }

    fun setContactInfoModel(
        targetSmsId: Long?,
        senderAddressId: Long,
        smsImportanceType: SmsImportanceType
    ) {
        this.targetSmsId = targetSmsId
        this.senderAddressId = senderAddressId
        this.smsImportanceType = smsImportanceType
        getContactInfoInboxModelUseCase
            .invoke(senderAddressId, smsImportanceType.value)
            .onEach { contact ->
                _contactInfoModel.value = contact
            }.launchIn(viewModelScope)
        initSmsMessageLoader()
    }

    private fun initSmsMessageLoader() {
        val loader = smsMessageLoaderFactory.create(
            senderAddressId = senderAddressId,
            pageSize = SMS_LIST_PAGE_SIZE,
            scope = viewModelScope
        )
        _messageLoader.value = loader
        viewModelScope.launch(Dispatchers.IO) { loader.reinitialize(smsImportanceType, targetSmsId) }
    }

    fun toggleMessageSelection(messageId: Long) {
        _messageLoader.value?.toggleMessageSelection(messageId)
    }

    fun clearMessageSelection() {
        _messageLoader.value?.clearAllSelections()
    }

    fun sendSms(context: Context, body: String?) {
        if (body.isNullOrBlank()) return
        val contact = _contactInfoModel.value

        // Try phoneNumber first, then check if senderName is a valid phone number
        val phoneNumber = contact?.phoneNumber?.takeIf { it.isNotEmpty() }
            ?: contact?.senderName?.takeIf { it.isValidPhoneNumber() }

        if (contact == null || phoneNumber.isNullOrEmpty()) {
            Log.e("SmsInbox", "No phone number found for senderAddressId=$senderAddressId")
            return
        }

        viewModelScope.launch {
            sendSmsUseCase.invoke(
                context,
                contact.senderAddressId,
                phoneNumber,
                body
            )
    }
}

    fun markSmsListAsRead(
        context: Context,
        senderAddressId: Long,
        callback: (smsIds: List<Long>) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.Default) {
            val smsIds = markSmsAsReadForSenderUseCase.invoke(context, senderAddressId)
            withContext(Dispatchers.Main) {
                callback(smsIds)
            }
        }
    }

    fun deleteSelectedMessages(context: Context, onCallback: () -> Unit) {
        viewModelScope.launch {
            val toBeDeletedMessages = selectedMessages.value
            val smsIds = mutableListOf<Long>()
            val androidSmsIds = mutableListOf<Long>()

            toBeDeletedMessages.forEach {
                smsIds.add(it.id)
                it.androidSmsId?.let { androidSmsId ->
                    androidSmsIds.add(androidSmsId)
                }
            }
            deleteSmsByIdsUseCase.invoke(context, smsIds, androidSmsIds)
            _messageLoader.value?.reinitialize(smsImportanceType, targetSmsId)
            onCallback()
        }
    }

    fun blockSender(callback: () -> Unit) {
        viewModelScope.launch(Dispatchers.Default) {
            blockSenderUseCase.invoke(senderAddressId)
            callback()
        }
    }
}