package com.summer.notifai.ui.banking.home

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.summer.core.data.model.BankAccount
import com.summer.notifai.R
import com.summer.notifai.databinding.ItemBankAccountBinding
import java.math.BigDecimal
import java.util.Calendar

class AccountAdapter(private val onClick: (BankAccount) -> Unit) :
    ListAdapter<BankAccount, AccountAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemBankAccountBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemBankAccountBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: BankAccount) = with(binding) {
            name.text = item.name
            identifier.text = listOfNotNull(
                item.instrumentType.lowercase().replaceFirstChar(Char::uppercase),
                item.maskedIdentifier,
            ).joinToString(" • ")
            identifier.visibility = if (identifier.text.isNullOrBlank()) View.GONE else View.VISIBLE
            val balance = item.lastReportedBalance
            reportedBalance.text = balance?.let {
                "${symbol(item.balanceCurrency)}${runCatching { BigDecimal(it).toPlainString() }.getOrDefault(it)}"
            } ?: root.context.getString(R.string.balance_not_reported)
            balanceAge.text = item.balanceObservedAt?.let {
                root.context.getString(
                    if (System.currentTimeMillis() - it > DateUtils.DAY_IN_MILLIS) R.string.balance_may_be_outdated
                    else R.string.balance_reported_recently
                )
            }.orEmpty()
            balanceAge.visibility = if (item.balanceObservedAt == null) View.GONE else View.VISIBLE
            val monthStart = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val month = item.transactions.filter { it.timestamp >= monthStart && it.currency in setOf("", "INR") }
            val debit = month.filter { it.direction == "DEBIT" }.mapNotNull { it.amount?.toBigDecimalOrNull() }.fold(BigDecimal.ZERO, BigDecimal::add)
            val credit = month.filter { it.direction == "CREDIT" }.mapNotNull { it.amount?.toBigDecimalOrNull() }.fold(BigDecimal.ZERO, BigDecimal::add)
            monthlyTotals.text = root.context.getString(R.string.account_monthly_totals, debit, credit)
            val logoId = root.resources.getIdentifier("bank_${item.logoKey}", "drawable", root.context.packageName)
            logo.setImageResource(if (logoId == 0) R.drawable.ic_wallet_24x24 else logoId)
            root.setOnClickListener { onClick(item) }
        }
    }

    private fun symbol(currency: String?) = when (currency) {
        "INR" -> "₹"; "USD" -> "$"; "EUR" -> "€"; "GBP" -> "£"; else -> currency?.plus(" ").orEmpty()
    }

    private object Diff : DiffUtil.ItemCallback<BankAccount>() {
        override fun areItemsTheSame(oldItem: BankAccount, newItem: BankAccount) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: BankAccount, newItem: BankAccount) = oldItem == newItem
    }
}
