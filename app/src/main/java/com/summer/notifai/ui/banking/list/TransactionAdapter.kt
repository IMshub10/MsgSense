package com.summer.notifai.ui.banking.list

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.summer.core.data.model.BankingTransaction
import com.summer.notifai.R
import com.summer.notifai.databinding.ItemTransactionBinding
import java.util.Date

class TransactionAdapter(
    private val onClick: (BankingTransaction) -> Unit,
    private val onLongClick: ((BankingTransaction) -> Unit)? = null,
) : ListAdapter<BankingTransaction, TransactionAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemTransactionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: BankingTransaction) = with(binding) {
            merchant.text = item.merchant
            amount.text = item.signedAmount
            val time = DateFormat.getMediumDateFormat(root.context).format(Date(item.timestamp)) +
                " • " + DateFormat.getTimeFormat(root.context).format(Date(item.timestamp))
            meta.text = listOfNotNull(time, item.bank, item.maskedAccount).joinToString(" • ")
            review.visibility = if (item.reviewState == "NEEDS_REVIEW") View.VISIBLE else View.GONE
            icon.setImageResource(
                when (item.category) {
                    "Food" -> R.drawable.ic_category_food
                    "Retail" -> R.drawable.ic_category_retail
                    "Coffee" -> R.drawable.ic_category_coffee
                    "Shopping" -> R.drawable.ic_category_shopping
                    "Transport" -> R.drawable.ic_category_transport
                    "Bills" -> R.drawable.ic_category_bills
                    else -> R.drawable.ic_category_other
                }
            )
            root.setOnClickListener { onClick(item) }
            root.setOnLongClickListener {
                onLongClick?.invoke(item)
                onLongClick != null
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<BankingTransaction>() {
        override fun areItemsTheSame(oldItem: BankingTransaction, newItem: BankingTransaction) =
            oldItem.extractionId == newItem.extractionId

        override fun areContentsTheSame(oldItem: BankingTransaction, newItem: BankingTransaction) =
            oldItem == newItem
    }
}
