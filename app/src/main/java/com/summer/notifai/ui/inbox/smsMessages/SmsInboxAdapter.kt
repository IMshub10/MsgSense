package com.summer.notifai.ui.inbox.smsMessages

import android.animation.ObjectAnimator
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.summer.core.android.sms.constants.Constants.SMS_HIGHLIGHT_ANIMATION
import com.summer.notifai.R
import com.summer.notifai.databinding.ItemReceivedSmsMessageBinding
import com.summer.notifai.databinding.ItemSentSmsMessageBinding
import com.summer.notifai.databinding.ItemSmsDateHeaderBinding
import com.summer.notifai.ui.datamodel.SmsInboxListItem
import kotlinx.coroutines.flow.StateFlow

class SmsInboxAdapter(
    private val highlightedIdFlow: StateFlow<Long?>,
    private val onItemClick: (SmsInboxListItem.Message) -> Unit,
    private val onLongItemClick: (SmsInboxListItem.Message) -> Unit,
) : ListAdapter<SmsInboxListItem, RecyclerView.ViewHolder>(DiffCallback) {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_RECEIVED_MESSAGE = 1
        private const val VIEW_TYPE_SENT_MESSAGE = 2

        private val DiffCallback = object : DiffUtil.ItemCallback<SmsInboxListItem>() {
            override fun areItemsTheSame(
                oldItem: SmsInboxListItem,
                newItem: SmsInboxListItem
            ): Boolean {
                return when {
                    oldItem is SmsInboxListItem.Message && newItem is SmsInboxListItem.Message ->
                        oldItem.data.id == newItem.data.id

                    oldItem is SmsInboxListItem.Header && newItem is SmsInboxListItem.Header ->
                        oldItem.header.label == newItem.header.label

                    else -> false
                }
            }

            override fun areContentsTheSame(
                oldItem: SmsInboxListItem,
                newItem: SmsInboxListItem
            ): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (val model = getItem(position)) {
            is SmsInboxListItem.Header -> VIEW_TYPE_HEADER
            is SmsInboxListItem.Message -> if (model.data.isIncoming) VIEW_TYPE_RECEIVED_MESSAGE else VIEW_TYPE_SENT_MESSAGE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val binding = ItemSmsDateHeaderBinding.inflate(inflater, parent, false)
                HeaderViewHolder(binding)
            }

            VIEW_TYPE_RECEIVED_MESSAGE -> {
                val binding = ItemReceivedSmsMessageBinding.inflate(inflater, parent, false)
                ReceivedMessageViewHolder(binding)
            }

            VIEW_TYPE_SENT_MESSAGE -> {
                val binding = ItemSentSmsMessageBinding.inflate(inflater, parent, false)
                SentMessageViewHolder(binding)
            }

            else -> throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is SmsInboxListItem.Message -> {
                if (item.data.isIncoming) (holder as ReceivedMessageViewHolder).bind(item)
                else (holder as SentMessageViewHolder).bind(item)
            }

            is SmsInboxListItem.Header -> (holder as HeaderViewHolder).bind(item)
        }
    }

    private fun bindHighlight(view: android.view.View, isHighlighted: Boolean) {
        if (isHighlighted) {
            val colorFrom = ContextCompat.getColor(view.context, R.color.orange_light)
            val colorTo = Color.TRANSPARENT
            ObjectAnimator.ofArgb(view, "backgroundColor", colorFrom, colorTo).apply {
                duration = SMS_HIGHLIGHT_ANIMATION
                start()
            }
        } else {
            view.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    inner class ReceivedMessageViewHolder(
        private val binding: ItemReceivedSmsMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SmsInboxListItem.Message) {
            binding.model = item.data
            bindHighlight(binding.root, highlightedIdFlow.value == item.data.id)
            binding.root.setOnClickListener { onItemClick(item) }
            binding.root.setOnLongClickListener {
                onLongItemClick(item)
                return@setOnLongClickListener true
            }
            binding.tvItemSmsMessageMessage.setOnClickListener { onItemClick(item) }
            binding.tvItemSmsMessageMessage.setOnLongClickListener {
                onLongItemClick(item)
                return@setOnLongClickListener true
            }
            binding.executePendingBindings()
        }
    }

    inner class SentMessageViewHolder(
        private val binding: ItemSentSmsMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SmsInboxListItem.Message) {
            binding.model = item.data
            bindHighlight(binding.root, highlightedIdFlow.value == item.data.id)
            binding.root.setOnClickListener { onItemClick(item) }
            binding.root.setOnLongClickListener {
                onLongItemClick(item)
                return@setOnLongClickListener true
            }
            binding.tvItemSmsMessageMessage.setOnClickListener { onItemClick(item) }
            binding.tvItemSmsMessageMessage.setOnLongClickListener {
                onLongItemClick(item)
                return@setOnLongClickListener true
            }
            binding.executePendingBindings()
        }
    }

    inner class HeaderViewHolder(
        private val binding: ItemSmsDateHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SmsInboxListItem.Header) {
            binding.label = item.header.label
            binding.executePendingBindings()
        }
    }
}
