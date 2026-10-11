package com.summer.notifai.ui.screenshot

import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.takahirom.roborazzi.captureRoboImage
import com.summer.core.data.model.BankAccount
import com.summer.core.data.model.CategoryTotal
import com.summer.core.data.model.MonthlyCashFlow
import com.summer.notifai.R
import com.summer.notifai.databinding.FragAccountDetailBinding
import com.summer.notifai.databinding.FragBankingHomeBinding
import com.summer.notifai.ui.banking.home.AccountAdapter
import com.summer.notifai.ui.banking.list.TransactionAdapter
import java.math.BigDecimal
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi")
class AccountFirstBankingScreenshotTest {
    private val account = BankAccount(
        id = 1,
        canonicalBank = "hdfc",
        instrumentType = "ACCOUNT",
        name = "Daily spending",
        maskedIdentifier = "••••1234",
        logoKey = "hdfc",
        isHidden = false,
        lastReportedBalance = "24500.25",
        balanceCurrency = "INR",
        balanceObservedAt = 1_781_002_800_000L,
        transactions = listOf(ScreenshotFixtures.expense, ScreenshotFixtures.income),
    )

    @Test
    fun dashboardAccounts() {
        val activity = ScreenshotTestHost.activity()
        val binding = FragBankingHomeBinding.bind(ScreenshotTestHost.inflate(activity, R.layout.frag_banking_home))
        val adapter = TransactionAdapter(onClick = {})
        binding.recentList.layoutManager = LinearLayoutManager(activity)
        binding.recentList.adapter = adapter
        adapter.submitList(account.transactions)
        binding.cashFlow.submit(
            listOf(MonthlyCashFlow(1, "INR", BigDecimal("1000"), BigDecimal("2500")))
        )
        binding.root.captureRoboImage()
    }

    @Test
    fun accountsTab() {
        val activity = ScreenshotTestHost.activity()
        val binding = FragBankingHomeBinding.bind(ScreenshotTestHost.inflate(activity, R.layout.frag_banking_home))
        binding.dashboard.visibility = View.GONE
        binding.accounts.visibility = View.VISIBLE
        binding.title.setText(R.string.accounts)
        val adapter = AccountAdapter {}
        binding.accountsList.layoutManager = LinearLayoutManager(activity)
        binding.accountsList.adapter = adapter
        adapter.submitList(listOf(account, account.copy(id = 2, name = "Unassigned HDFC Bank", instrumentType = "UNASSIGNED", maskedIdentifier = null, lastReportedBalance = null)))
        binding.empty.visibility = View.GONE
        binding.root.captureRoboImage()
    }

    @Test
    fun insightsTab() {
        val activity = ScreenshotTestHost.activity()
        val binding = FragBankingHomeBinding.bind(ScreenshotTestHost.inflate(activity, R.layout.frag_banking_home))
        binding.dashboard.visibility = View.GONE
        binding.insights.visibility = View.VISIBLE
        binding.title.setText(R.string.insights)
        binding.categoryDonut.submit(
            listOf(
                CategoryTotal("Food", "INR", BigDecimal("600")),
                CategoryTotal("Shopping", "INR", BigDecimal("400")),
            )
        )
        binding.root.captureRoboImage()
    }

    @Test
    fun accountDetail() {
        val activity = ScreenshotTestHost.activity()
        val binding = FragAccountDetailBinding.bind(ScreenshotTestHost.inflate(activity, R.layout.frag_account_detail))
        binding.name.text = account.name
        binding.identity.text = "Account • ${account.maskedIdentifier}"
        binding.balance.text = "INR ${account.lastReportedBalance}"
        binding.balanceAge.text = activity.getString(R.string.balance_may_be_outdated)
        val adapter = TransactionAdapter(onClick = {})
        binding.transactions.layoutManager = LinearLayoutManager(activity)
        binding.transactions.adapter = adapter
        adapter.submitList(account.transactions)
        binding.root.captureRoboImage()
    }
}
