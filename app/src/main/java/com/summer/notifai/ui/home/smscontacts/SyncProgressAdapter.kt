package com.summer.notifai.ui.home.smscontacts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.summer.notifai.R
import com.summer.notifai.databinding.ItemSyncProgressBinding
import com.summer.notifai.ui.home.HomeViewModel

/**
 * Single-item adapter that shows sync progress as a header in the RecyclerView.
 * Visibility is controlled by returning 0 or 1 from getItemCount to avoid
 * ConcatAdapter add/remove which causes scroll position jumps.
 */
class SyncProgressAdapter : RecyclerView.Adapter<SyncProgressAdapter.SyncProgressViewHolder>() {

    private var progress: HomeViewModel.SyncProgress? = null

    fun updateProgress(newProgress: HomeViewModel.SyncProgress?) {
        progress = newProgress
        notifyItemChanged(0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SyncProgressViewHolder {
        val binding = ItemSyncProgressBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SyncProgressViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SyncProgressViewHolder, position: Int) {
        holder.bind(progress)
    }

    override fun getItemCount(): Int = 1

    class SyncProgressViewHolder(
        private val binding: ItemSyncProgressBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(progress: HomeViewModel.SyncProgress?) {
            val context = binding.root.context
            if (progress != null && progress.total > 0) {
                val percent = (progress.processed * 100) / progress.total
                binding.tvSyncStatus.text = context.getString(
                    R.string.syncing_progress,
                    progress.processed,
                    progress.total,
                    percent
                )
                binding.progressSync.isIndeterminate = false
                binding.progressSync.max = progress.total
                binding.progressSync.progress = progress.processed
            } else {
                binding.tvSyncStatus.text = context.getString(R.string.syncing)
                binding.progressSync.isIndeterminate = true
            }
        }
    }
}
