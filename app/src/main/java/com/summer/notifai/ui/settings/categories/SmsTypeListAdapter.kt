package com.summer.notifai.ui.settings.categories

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.summer.core.data.local.entities.SmsClassificationTypeEntity
import com.summer.notifai.databinding.ItemSmsClassificationHeaderBinding
import com.summer.notifai.databinding.ItemSmsTypeBinding
import com.summer.notifai.ui.datamodel.SmsTypeUiModel

class SmsTypeListAdapter(
    private val onToggleChanged: (SmsClassificationTypeEntity, Boolean) -> Unit
) : ListAdapter<SmsTypeUiModel, RecyclerView.ViewHolder>(DiffCallback) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is SmsTypeUiModel.Header -> 0
        is SmsTypeUiModel.Item -> 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = when (viewType) {
        0 -> HeaderViewHolder(
            ItemSmsClassificationHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
        1 -> ItemViewHolder(
            ItemSmsTypeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
        else -> throw IllegalStateException("Invalid viewType $viewType")
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is SmsTypeUiModel.Header -> (holder as HeaderViewHolder).bind(item.title)
            is SmsTypeUiModel.Item -> (holder as ItemViewHolder).bind(item.entity)
        }
    }

    inner class HeaderViewHolder(private val binding: ItemSmsClassificationHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(title: String) {
            binding.title = title
        }
    }

    inner class ItemViewHolder(private val binding: ItemSmsTypeBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(entity: SmsClassificationTypeEntity) {
            binding.model = entity
            
            binding.switchImportant.setOnCheckedChangeListener(null)
            binding.switchImportant.isChecked = entity.isImportant
            binding.switchImportant.setOnCheckedChangeListener { _, isChecked ->
                onToggleChanged(entity, isChecked)
            }
            
            binding.executePendingBindings()
        }
    }

    companion object {
        val DiffCallback = object : DiffUtil.ItemCallback<SmsTypeUiModel>() {
            override fun areItemsTheSame(oldItem: SmsTypeUiModel, newItem: SmsTypeUiModel) =
                oldItem is SmsTypeUiModel.Item && newItem is SmsTypeUiModel.Item &&
                        oldItem.entity.id == newItem.entity.id ||
                        oldItem is SmsTypeUiModel.Header && newItem is SmsTypeUiModel.Header &&
                        oldItem.title == newItem.title

            override fun areContentsTheSame(oldItem: SmsTypeUiModel, newItem: SmsTypeUiModel) =
                oldItem == newItem
        }
    }
}