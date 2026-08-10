package com.summer.ner.banking

import com.summer.core.data.local.dao.NerDao
import com.summer.core.data.local.model.NerProcessingSummary
import com.summer.core.data.local.entities.BankingTransactionOverrideEntity
import com.summer.core.data.model.BankingTransaction
import com.summer.core.data.model.BankAccount
import com.summer.core.data.model.BankingOverview
import com.summer.core.data.model.CategoryTotal
import com.summer.core.data.model.CurrencyTotals
import com.summer.core.data.model.MonthlyCashFlow
import java.math.BigDecimal
import java.util.Calendar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BankingRepository @Inject constructor(
    private val dao: NerDao,
    private val accountOrganizer: BankAccountOrganizer,
) {
    fun observeTransactions(): Flow<List<BankingTransaction>> =
        dao.observeTransactions().map { rows -> rows.map(BankingTransaction::from) }

    fun observeProcessingSummary(): Flow<NerProcessingSummary> = dao.observeProcessingSummary()

    fun observeTransaction(extractionId: Long): Flow<BankingTransaction?> =
        dao.observeTransaction(extractionId).map { it?.let(BankingTransaction::from) }

    suspend fun transactionById(extractionId: Long): BankingTransaction? =
        dao.transactionById(extractionId)?.let(BankingTransaction::from)

    fun observeAccounts(): Flow<List<BankAccount>> = combine(
        dao.observeBankAccounts(),
        dao.observeAccountLinks(),
        dao.observeBalanceObservations(),
        observeTransactions(),
    ) { accounts, links, balances, transactions ->
        val accountByExtraction = links.associate { it.extractionId to it.accountId }
        accounts.mapNotNull { account ->
            val accountTransactions = transactions.filter {
                accountByExtraction[it.extractionId] == account.id
            }
            if (accountTransactions.isEmpty()) return@mapNotNull null
            val balance = balances.firstOrNull { it.accountId == account.id }
            BankAccount(
                id = account.id,
                canonicalBank = account.canonicalBank,
                instrumentType = account.instrumentType,
                name = account.customName ?: account.generatedName,
                maskedIdentifier = account.maskedIdentifier,
                logoKey = account.logoKey,
                isHidden = account.isHidden,
                lastReportedBalance = balance?.balance,
                balanceCurrency = balance?.currency,
                balanceObservedAt = balance?.observedAt,
                transactions = accountTransactions,
            )
        }
    }

    fun observeAccount(accountId: Long): Flow<BankAccount?> =
        observeAccounts().map { accounts -> accounts.firstOrNull { it.id == accountId } }

    fun observeOverview(): Flow<BankingOverview> = combine(observeAccounts(), observeTransactions()) {
            accounts, transactions ->
        val now = System.currentTimeMillis()
        BankingOverview(
            accounts = accounts,
            recentTransactions = transactions.take(5),
            currentMonthTotals = totals(transactions, startOfMonth(now), now),
            sixMonthCashFlow = cashFlow(transactions, now),
        )
    }

    fun observeInsights(start: Long, endExclusive: Long): Flow<Pair<List<CurrencyTotals>, List<CategoryTotal>>> =
        observeTransactions().map { transactions ->
            val selected = transactions.filter { it.timestamp in start until endExclusive }
            totals(selected, start, endExclusive) to selected
                .filter { it.direction == "DEBIT" }
                .mapNotNull { transaction ->
                    transaction.amount?.toBigDecimalOrNull()?.let {
                        CategoryTotal(transaction.category, transaction.currency.ifBlank { "Unknown" }, it)
                    }
                }
                .groupBy { it.currency to it.category }
                .map { (key, values) -> CategoryTotal(key.second, key.first, values.sumOf { it.amount }) }
        }

    suspend fun organizeAccounts() = accountOrganizer.organizeAll()

    suspend fun renameAccount(accountId: Long, name: String?) =
        dao.renameBankAccount(accountId, name?.trim()?.takeIf(String::isNotEmpty), System.currentTimeMillis())

    suspend fun setAccountHidden(accountId: Long, hidden: Boolean) =
        dao.setBankAccountHidden(accountId, hidden, System.currentTimeMillis())

    suspend fun assignTransaction(extractionId: Long, accountId: Long) =
        dao.reassignTransaction(extractionId, accountId, System.currentTimeMillis())

    suspend fun unassignTransaction(extractionId: Long) {
        val bank = transactionById(extractionId)?.bank
        accountOrganizer.moveToUnassigned(extractionId, bank)
    }

    suspend fun mergeAccounts(sourceId: Long, targetId: Long): Boolean {
        val accounts = dao.observeBankAccounts().first()
        val source = accounts.firstOrNull { it.id == sourceId } ?: return false
        val target = accounts.firstOrNull { it.id == targetId } ?: return false
        if (source.canonicalBank != target.canonicalBank || source.instrumentType != target.instrumentType) {
            return false
        }
        dao.mergeAccounts(sourceId, targetId, System.currentTimeMillis())
        return true
    }

    suspend fun saveOverride(
        extractionId: Long,
        merchant: String,
        amount: String,
        currency: String,
        direction: String,
        category: String,
        paymentMethod: String,
    ) {
        val now = System.currentTimeMillis()
        dao.upsertTransactionOverride(
            BankingTransactionOverrideEntity(
                runId = extractionId,
                merchant = merchant.trim(),
                amount = amount,
                currency = currency,
                direction = direction,
                category = category,
                paymentMethod = paymentMethod,
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    private fun totals(transactions: List<BankingTransaction>, start: Long, end: Long): List<CurrencyTotals> =
        transactions.filter { it.timestamp in start until end }
            .groupBy { it.currency.ifBlank { "Unknown" } }
            .map { (currency, values) ->
                CurrencyTotals(
                    currency = currency,
                    debit = values.sum("DEBIT"),
                    credit = values.sum("CREDIT"),
                    reversal = values.sum("REVERSAL"),
                    excludedCount = values.count { it.amount?.toBigDecimalOrNull() == null },
                )
            }.sortedByDescending { it.currency == "INR" }

    private fun cashFlow(transactions: List<BankingTransaction>, now: Long): List<MonthlyCashFlow> {
        val starts = (5 downTo 0).map { offset ->
            Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.MONTH, -offset)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
        return starts.flatMapIndexed { index, start ->
            val end = starts.getOrNull(index + 1) ?: Calendar.getInstance().apply {
                timeInMillis = start
                add(Calendar.MONTH, 1)
            }.timeInMillis
            transactions.filter { it.timestamp in start until end }
                .groupBy { it.currency.ifBlank { "Unknown" } }
                .map { (currency, values) ->
                    MonthlyCashFlow(start, currency, values.sum("DEBIT"), values.sum("CREDIT"))
                }
        }
    }

    private fun List<BankingTransaction>.sum(direction: String): BigDecimal =
        filter { it.direction == direction }.mapNotNull { it.amount?.toBigDecimalOrNull() }
            .fold(BigDecimal.ZERO, BigDecimal::add)

    private fun startOfMonth(timestamp: Long): Long = Calendar.getInstance().apply {
        timeInMillis = timestamp
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
