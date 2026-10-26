package com.summer.ner.banking

import android.app.Application
import android.content.Context
import org.robolectric.RuntimeEnvironment
import com.summer.ner.BankingTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class BankAccountMergeTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private lateinit var fixture: BankingTestDatabase
    private lateinit var organizer: BankAccountOrganizer
    private lateinit var repository: BankingRepository

    @Before
    fun setUp() {
        fixture = BankingTestDatabase(context)
        organizer = BankAccountOrganizer(context, fixture.dao)
        repository = BankingRepository(fixture.dao, organizer)
    }

    @After
    fun tearDown() = fixture.close()

    @Test
    fun successiveMergesKeepNewHistoryAndLatestBalanceOnSurvivingAccount() = runBlocking {
        val (runA, a) = account("0011111111")
        val (_, b) = account("0022222222")
        val (_, c) = account("0033333333")
        assertTrue(repository.mergeAccounts(a, b))
        assertTrue(repository.mergeAccounts(b, c))
        val newRun = fixture.insertRun()
        fixture.complete(newRun, account = "0011111111", balance = "900")
        organizer.organizeAll()

        val visible = repository.observeAccounts().first().single()
        assertEquals(c, visible.id)
        assertEquals(4, visible.transactions.size)
        assertTrue(visible.transactions.any { it.extractionId == newRun })
        assertEquals("900", visible.lastReportedBalance)
        assertEquals(b, fixture.dao.bankAccountById(a)!!.mergeTargetId)
        assertEquals(c, fixture.dao.survivingBankAccount(a).id)
        // A stale UI target must also resolve to the survivor.
        repository.assignTransaction(runA, a)
        assertEquals(c.toString(), fixture.string("SELECT account_id FROM sms_transaction_account_links WHERE extraction_id = $runA"))
    }

    @Test
    fun startupRepairRedirectsPreviouslyStrandedLinksAndBalances() = runBlocking {
        val (run, a) = account("0011111111")
        val (_, b) = account("0022222222")
        assertTrue(repository.mergeAccounts(a, b))
        fixture.execute("UPDATE sms_transaction_account_links SET account_id = $a WHERE extraction_id = $run")
        fixture.execute("UPDATE bank_account_balance_observations SET account_id = $a WHERE source_extraction_id = $run")
        fixture.dao.repairBankingData(com.summer.ner.NerPipelineMetadata.PIPELINE_FINGERPRINT, 5)
        assertEquals(b.toString(), fixture.string("SELECT account_id FROM sms_transaction_account_links WHERE extraction_id = $run"))
        assertEquals(b.toString(), fixture.string("SELECT account_id FROM bank_account_balance_observations WHERE source_extraction_id = $run"))
    }

    @Test
    fun rejectsCyclesMissingTargetsAndSelfMerge() = runBlocking {
        val (_, a) = account("0011111111")
        val (_, b) = account("0022222222")
        assertFalse(repository.mergeAccounts(a, a))
        fixture.execute("UPDATE bank_accounts SET merge_target_id = $b WHERE id = $a")
        fixture.execute("UPDATE bank_accounts SET merge_target_id = $a WHERE id = $b")
        assertInvalidTarget(a)
        fixture.execute("UPDATE bank_accounts SET merge_target_id = 999999 WHERE id = $a")
        assertInvalidTarget(a)
    }

    private suspend fun account(identifier: String): Pair<Long, Long> {
        val run = fixture.insertRun()
        fixture.complete(run, account = identifier)
        organizer.organizeAll()
        return run to fixture.dao.transactionById(run)!!.accountId!!
    }

    private suspend fun assertInvalidTarget(id: Long) {
        try {
            fixture.dao.survivingBankAccount(id)
            fail("Expected invalid merge target to be rejected")
        } catch (_: IllegalStateException) { }
    }
}
