package com.summer.notifai.ui.home.smscontacts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.summer.notifai.databinding.ItemSmsContactBinding
import com.summer.notifai.ui.datamodel.ContactMessageInfoDataModel

class SmsContactListPagingAdapter(
    private val onItemClick: (ContactMessageInfoDataModel) -> Unit
) : PagingDataAdapter<ContactMessageInfoDataModel, SmsContactListPagingAdapter.ContactViewHolder>(DiffCallback) {

    // Set to true during sync to ignore unread count changes
    var isSyncing: Boolean = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemSmsContactBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ContactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        getItem(position)?.let { holder.bind(it) }
    }

    inner class ContactViewHolder(
        private val binding: ItemSmsContactBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ContactMessageInfoDataModel) {
            binding.model = item
            binding.root.setOnClickListener { onItemClick(item) }
            binding.executePendingBindings()
        }
    }

    companion object {
        val DiffCallback = object : DiffUtil.ItemCallback<ContactMessageInfoDataModel>() {
            override fun areItemsTheSame(
                oldItem: ContactMessageInfoDataModel,
                newItem: ContactMessageInfoDataModel
            ): Boolean = oldItem.senderAddressId == newItem.senderAddressId

            override fun areContentsTheSame(
                oldItem: ContactMessageInfoDataModel,
                newItem: ContactMessageInfoDataModel
            ): Boolean {
                // Compare all fields except unreadCount when syncing
                // Note: This relies on data class comparison, but unreadCount 
                // changes are tolerated when isSyncing is true (handled in payload)
                return oldItem.senderAddressId == newItem.senderAddressId &&
                       oldItem.senderName == newItem.senderName &&
                       oldItem.lastMessage == newItem.lastMessage &&
                       oldItem.lastMessageDate == newItem.lastMessageDate &&
                       oldItem.senderType == newItem.senderType &&
                       oldItem.rawAddress == newItem.rawAddress
                // Deliberately exclude unreadCount to prevent rebind on count changes
            }

            override fun getChangePayload(
                oldItem: ContactMessageInfoDataModel,
                newItem: ContactMessageInfoDataModel
            ): Any? {
                // If only unreadCount changed, return a payload to do partial update
                if (oldItem.senderAddressId == newItem.senderAddressId &&
                    oldItem.senderName == newItem.senderName &&
                    oldItem.lastMessage == newItem.lastMessage &&
                    oldItem.lastMessageDate == newItem.lastMessageDate &&
                    oldItem.senderType == newItem.senderType &&
                    oldItem.rawAddress == newItem.rawAddress &&
                    oldItem.unreadCount != newItem.unreadCount
                ) {
                    return PAYLOAD_UNREAD_COUNT
                }
                return null
            }
        }

        const val PAYLOAD_UNREAD_COUNT = "payload_unread_count"
    }
}