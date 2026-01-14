package com.summer.notifai.ui.inbox.smsMessages

import android.app.ProgressDialog
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.summer.core.android.notification.AppNotificationManager
import com.summer.core.android.permission.manager.IPermissionManager
import com.summer.core.android.sms.constants.Constants.DATE_FLOATER_SHOW_TIME
import com.summer.core.di.ChatSessionTracker
import com.summer.core.ui.BaseFragment
import com.summer.core.ui.model.SmsImportanceType
import com.summer.core.util.DateUtils
import com.summer.notifai.R
import com.summer.notifai.databinding.FragSmsInboxBinding
import com.summer.notifai.ui.MainActivity
import com.summer.notifai.ui.common.SafeLinearLayoutManager
import com.summer.notifai.ui.datamodel.SmsInboxListItem
import com.summer.notifai.ui.inbox.SmsInboxViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SmsInboxFrag : BaseFragment<FragSmsInboxBinding>() {
    
    override val layoutResId: Int
        get() = R.layout.frag_sms_inbox

    private val smsInboxViewModel: SmsInboxViewModel by viewModels()

    @Inject
    lateinit var chatSessionTracker: ChatSessionTracker

    @Inject
    lateinit var appNotificationManager: AppNotificationManager

    private var _smsInboxAdapter: SmsInboxAdapter? = null
    private val smsInboxAdapter get() = _smsInboxAdapter!!

    private val scrollHandler = Handler(Looper.getMainLooper())
    private var scrollHideRunnable: Runnable? = null
    private val dayLabelCache = mutableMapOf<Long, String>()
    private var lastFloatingDateLabel: String? = null

    private val progressDialog by lazy {
        ProgressDialog(requireContext()).apply {
            setCancelable(false)
            setTitle(getString(R.string.syncing))
        }
    }

    @Inject
    lateinit var permissionManager: IPermissionManager

    override fun onFragmentReady(instanceState: Bundle?) {
        super.onFragmentReady(instanceState)
        mBinding.lifecycleOwner = viewLifecycleOwner
        mBinding.viewModel = smsInboxViewModel
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Handle keyboard insets programmatically
        setupKeyboardInsets()
        setupToolbar()

        val args = SmsInboxFragArgs.fromBundle(requireArguments())
        smsInboxViewModel.setContactInfoModel(
            targetSmsId = if (args.targetSmsId == 0L) null else args.targetSmsId,
            senderAddressId = args.senderAddressId,
            smsImportanceType = SmsImportanceType.fromValue(args.smsImportanceType)
                ?: SmsImportanceType.ALL
        )
        view.post {
            setupRecyclerView()
            observeMessages()
            listeners()
        }
    }

    private fun setupKeyboardInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(mBinding.root) { view, windowInsets ->
            val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(0, 0, 0, imeInsets.bottom)
            windowInsets
        }
    }

    private fun setupToolbar() {
        mBinding.ivFragSmsInboxBack.setOnClickListener {
            findNavController().popBackStack()
        }
        mBinding.ivFragSmsInboxSearch.setOnClickListener {
            val bundle = bundleOf(
                "query" to "",
                "searchType" to "1",
                "senderAddressId" to smsInboxViewModel.senderAddressId.toString()
            )
            findNavController().navigate(R.id.action_inbox_to_search_list, bundle)
        }
        mBinding.ivFragSmsInboxBlock.setOnClickListener {
            showYesNoDialog(
                requireContext(),
                getString(R.string.block),
                getString(R.string.blocked_sender_message),
                {
                    progressDialog.show()
                    smsInboxViewModel.blockSender {
                        progressDialog.dismiss()
                        if (isAdded && view != null) {
                            findNavController().popBackStack()
                        }
                    }
                },
                {})
        }
    }

    override fun onResume() {
        super.onResume()
        val args = SmsInboxFragArgs.fromBundle(requireArguments())
        smsInboxViewModel.markSmsListAsRead(requireContext(), args.senderAddressId) {
            appNotificationManager.clearNotificationForSender(it)
        }
        chatSessionTracker.activeSenderAddressId = smsInboxViewModel.senderAddressId
    }

    private fun listeners() {
        smsInboxViewModel.isSendEnabled.observe(viewLifecycleOwner) {
            mBinding.btFragSmsInboxSend.isEnabled = it
        }
        smsInboxViewModel.isScrollDownButtonVisible.observe(viewLifecycleOwner) {
            mBinding.btFragSmsInboxScrollDown.isVisible = it
        }
        mBinding.btFragSmsInboxScrollDown.setOnClickListener {
            mBinding.rvSmsMessages.post {
                mBinding.rvSmsMessages.stopScroll()
                mBinding.rvSmsMessages.smoothScrollToPosition(0)
            }
        }
        mBinding.btFragSmsInboxSend.setOnClickListener {
            if (permissionManager.isDefaultSms()) {
                val message = mBinding.etFragSmsInboxMessage.text.toString()
                mBinding.etFragSmsInboxMessage.text?.clear()
                smsInboxViewModel.sendSms(requireContext(), message)
            } else {
                // Delegate to MainActivity's centralized prompt
                (activity as? MainActivity)?.promptToSetDefaultSmsApp()
            }
        }
        mBinding.ivFragSmsInboxClose.setOnClickListener {
            smsInboxViewModel.clearMessageSelection()
        }
        mBinding.btFragSmsInboxCopy.setOnClickListener {
            copySelectedMessagesToClipboard(requireContext())
            smsInboxViewModel.clearMessageSelection()
        }
        mBinding.btFragSmsInboxDelete.setOnClickListener {
            showYesNoDialog(
                requireContext(),
                getString(R.string.delete),
                getString(R.string.once_deleted_messages_can_t_be_restored),
                {
                    progressDialog.show()
                    smsInboxViewModel.deleteSelectedMessages(requireContext()) {
                        progressDialog.dismiss()
                    }
                },
                {})
        }
        mBinding.btFragSmsInboxReport.setOnClickListener {
            smsInboxViewModel.clearMessageSelection()
            // TODO(API integration to send to remote server, after showing a dialog containing privacy condition.)
        }
    }

    private fun showYesNoDialog(
        context: Context,
        title: String,
        message: String,
        onYes: () -> Unit,
        onNo: () -> Unit = {}
    ) {
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(getString(R.string.yes)) { dialog, _ ->
                dialog.dismiss()
                onYes()
            }
            .setNegativeButton(getString(R.string.no)) { dialog, _ ->
                dialog.dismiss()
                onNo()
            }
            .show()
    }

    private fun setupRecyclerView() {
        val layoutManager = SafeLinearLayoutManager(requireContext()).apply {
            reverseLayout = true
            stackFromEnd = true
        }

        mBinding.rvSmsMessages.layoutManager = layoutManager

        _smsInboxAdapter = SmsInboxAdapter(
            smsInboxViewModel.highlightedMessageId, {
                if (smsInboxViewModel.selectedMessages.value.isNotEmpty()) {
                    smsInboxViewModel.toggleMessageSelection(it.data.id)
                }
            }, {
                smsInboxViewModel.toggleMessageSelection(it.data.id)
            }
        )
        mBinding.rvSmsMessages.adapter = smsInboxAdapter

        mBinding.rvSmsMessages.alpha = 0f
        mBinding.rvSmsMessages.animate().alpha(1f).setDuration(250).start()

        mBinding.rvSmsMessages.itemAnimator?.apply {
            addDuration = 120
            removeDuration = 120
            changeDuration = 100
            moveDuration = 100
        }

        mBinding.rvSmsMessages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val lm = recyclerView.layoutManager as? SafeLinearLayoutManager ?: return
                val topPos = lm.findLastVisibleItemPosition()
                val visibleTop = lm.findFirstVisibleItemPosition()
                smsInboxViewModel.isAtBottom.value = visibleTop <= 1

                if (topPos != RecyclerView.NO_POSITION) {
                    val item = smsInboxAdapter.currentList.getOrNull(topPos) ?: return
                    val label = when (item) {
                        is SmsInboxListItem.Message -> dayLabelCache.getOrPut(item.data.dateInEpoch) {
                            DateUtils.formatDayHeader(item.data.dateInEpoch)
                        }
                        is SmsInboxListItem.Header -> item.header.label
                    }
                    if (label != lastFloatingDateLabel) {
                        showFloatingDateLabel(label)
                        lastFloatingDateLabel = label
                    }
                }
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                smsInboxViewModel.isRecyclerViewScrolling.value =
                    recyclerView.scrollState != RecyclerView.SCROLL_STATE_IDLE
            }
        })
    }

    private fun showFloatingDateLabel(label: String) {
        mBinding.tvFragSmsInboxFloatingDate.text = label.trim()
        mBinding.tvFragSmsInboxFloatingDate.visibility = View.VISIBLE

        scrollHideRunnable?.let { scrollHandler.removeCallbacks(it) }

        scrollHideRunnable = Runnable {
            mBinding.tvFragSmsInboxFloatingDate.visibility = View.GONE
        }
        scrollHideRunnable?.let {
            scrollHandler.postDelayed(it, DATE_FLOATER_SHOW_TIME)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeMessages() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                smsInboxViewModel.messageLoader
                    .filterNotNull()
                    .flatMapLatest { it.messages }
                    .collectLatest { list ->
                        smsInboxAdapter.submitList(list) {
                            if (list.isNotEmpty()) {
                                scrollToTargetIfPresent(smsInboxViewModel.targetSmsId)
                            }
                        }
                    }
            }
        }
    }

    private fun scrollToTargetIfPresent(targetId: Long?) {
        if (smsInboxViewModel.isListInitScrollCalled && !smsInboxViewModel.isAtBottom.value) return

        smsInboxViewModel.isListInitScrollCalled = true
        smsInboxViewModel.targetSmsId = null

        mBinding.rvSmsMessages.post {
            val index = if (targetId == null) 0 else smsInboxAdapter.currentList.indexOfFirst {
                it is SmsInboxListItem.Message && it.data.id == targetId
            }
            if (index != -1) {
                val layoutManager = mBinding.rvSmsMessages.layoutManager as SafeLinearLayoutManager
                layoutManager.scrollToPosition(index)
                targetId?.let {
                    smsInboxViewModel.flashMessage(targetId) {
                        val newIndex = smsInboxAdapter.currentList.indexOfFirst {
                            it is SmsInboxListItem.Message && it.data.id == targetId
                        }
                        if (newIndex != -1) {
                            smsInboxAdapter.notifyItemChanged(newIndex)
                        }
                    }
                    smsInboxAdapter.notifyItemChanged(index)
                }
            }
        }
    }

    private fun copySelectedMessagesToClipboard(context: Context) {
        val messages = smsInboxViewModel.selectedMessages.value
            .sortedBy { it.dateInEpoch } // Optional: keep chronological order
            .joinToString(separator = "\n\n") { it.message } // Double line spacing

        val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Selected Sms Messages", messages)
        clipboard.setPrimaryClip(clip)
    }

    override fun onDestroyView() {
        scrollHideRunnable?.let { scrollHandler.removeCallbacks(it) }
        smsInboxViewModel.cancelFlash()
        dayLabelCache.clear()
        lastFloatingDateLabel = null
        _smsInboxAdapter = null
        super.onDestroyView()
    }
}
