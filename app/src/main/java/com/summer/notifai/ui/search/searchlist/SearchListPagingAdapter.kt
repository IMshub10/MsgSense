package com.summer.notifai.ui.search.searchlist

import android.content.Context
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.core.content.res.ResourcesCompat
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.summer.notifai.R
import com.summer.notifai.databinding.ItemNewContactBinding
import com.summer.notifai.databinding.ItemReceivedSearchSmsMessageBinding
import com.summer.notifai.databinding.ItemSentSearchSmsMessageBinding
import com.summer.notifai.databinding.ItemSmsContactBinding
import com.summer.notifai.ui.datamodel.GlobalSearchListItem
import java.util.regex.Pattern

class SearchListPagingAdapter(
    private val itemClickListener: GlobalSearchItemClickListener,
    private val query: String
) : PagingDataAdapter<GlobalSearchListItem, RecyclerView.ViewHolder>(GlobalSearchDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_SMS_RECEIVED = 1
        private const val VIEW_TYPE_SMS_SENT = 2
        private const val VIEW_TYPE_CONVERSATION = 3
        private const val VIEW_TYPE_CONTACT = 4
    }

    @ColorRes
    private val highlightColor: Int = R.color.orange_dark

    private fun String.highlightQuery(context: Context): CharSequence {
        if (query.isBlank()) return this

        val pattern = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(this)
        val spannable = SpannableStringBuilder(this)

        while (matcher.find()) {
            spannable.setSpan(
                BackgroundColorSpan(
                    ResourcesCompat.getColor(context.resources, highlightColor, null)
                ),
                matcher.start(),
                matcher.end(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        return spannable
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is GlobalSearchListItem.SmsItem -> if (item.data.isIncoming) VIEW_TYPE_SMS_RECEIVED else VIEW_TYPE_SMS_SENT
            is GlobalSearchListItem.ConversationItem -> VIEW_TYPE_CONVERSATION
            is GlobalSearchListItem.ContactItem -> VIEW_TYPE_CONTACT
            is GlobalSearchListItem.SectionHeader -> VIEW_TYPE_SMS_RECEIVED // Should not appear in this adapter
            null -> VIEW_TYPE_SMS_RECEIVED // Default or handle placeholder
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when (viewType) {
            VIEW_TYPE_SMS_RECEIVED -> {
                val binding = ItemReceivedSearchSmsMessageBinding.inflate(inflater, parent, false)
                SmsReceivedViewHolder(binding)
            }

            VIEW_TYPE_SMS_SENT -> {
                val binding = ItemSentSearchSmsMessageBinding.inflate(inflater, parent, false)
                SmsSentViewHolder(binding)
            }

            VIEW_TYPE_CONVERSATION -> {
                val binding = ItemSmsContactBinding.inflate(inflater, parent, false)
                ConversationViewHolder(binding)
            }

            VIEW_TYPE_CONTACT -> {
                val binding = ItemNewContactBinding.inflate(inflater, parent, false)
                ContactViewHolder(binding)
            }

            else -> throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position) ?: return
        when (item) {
            is GlobalSearchListItem.SmsItem -> {
                if (item.data.isIncoming) {
                    (holder as SmsReceivedViewHolder).bind(item)
                } else {
                    (holder as SmsSentViewHolder).bind(item)
                }
            }

            is GlobalSearchListItem.ConversationItem -> (holder as ConversationViewHolder).bind(item)
            is GlobalSearchListItem.ContactItem -> (holder as ContactViewHolder).bind(item)
            else -> {
                throw IllegalArgumentException("Unsupported Item")
            }
        }
    }

    inner class SmsReceivedViewHolder(private val binding: ItemReceivedSearchSmsMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: GlobalSearchListItem.SmsItem) {
            binding.model = item.data
            binding.tvItemSmsMessageMessage.text =
                item.data.message.highlightQuery(binding.tvItemSmsMessageMessage.context)
            binding.root.setOnClickListener { itemClickListener.onSmsClicked(item) }
            binding.executePendingBindings()
        }
    }

    inner class SmsSentViewHolder(private val binding: ItemSentSearchSmsMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: GlobalSearchListItem.SmsItem) {
            binding.model = item.data
            binding.tvItemSmsMessageMessage.text =
                item.data.message.highlightQuery(binding.tvItemSmsMessageMessage.context)
            binding.root.setOnClickListener { itemClickListener.onSmsClicked(item) }
            binding.executePendingBindings()
        }
    }

    inner class ConversationViewHolder(private val binding: ItemSmsContactBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: GlobalSearchListItem.ConversationItem) {
            binding.model = item.data
            binding.tvItemSmsContactSenderName.text =
                item.data.senderName.highlightQuery(binding.tvItemSmsContactSenderName.context)
            binding.root.setOnClickListener { itemClickListener.onConversationClicked(item) }
            binding.executePendingBindings()
        }
    }

    inner class ContactViewHolder(private val binding: ItemNewContactBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: GlobalSearchListItem.ContactItem) {
            binding.model = item.data
            binding.tvItemNewContactSenderName.text =
                item.data.contactName.highlightQuery(binding.tvItemNewContactSenderName.context)
            binding.tvItemNewContactSenderAddress.text =
                item.data.phoneNumber.highlightQuery(binding.tvItemNewContactSenderAddress.context)
            binding.root.setOnClickListener { itemClickListener.onContactClicked(item) }
            binding.executePendingBindings()
        }
    }

    class GlobalSearchDiffCallback : DiffUtil.ItemCallback<GlobalSearchListItem>() {
        override fun areItemsTheSame(
            old: GlobalSearchListItem,
            new: GlobalSearchListItem
        ): Boolean = old == new

        override fun areContentsTheSame(
            old: GlobalSearchListItem,
            new: GlobalSearchListItem
        ): Boolean = old == new
    }

    interface GlobalSearchItemClickListener {
        fun onSmsClicked(item: GlobalSearchListItem.SmsItem)
        fun onConversationClicked(item: GlobalSearchListItem.ConversationItem)
        fun onContactClicked(item: GlobalSearchListItem.ContactItem)
    }
}