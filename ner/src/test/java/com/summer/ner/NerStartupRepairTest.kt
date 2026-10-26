package com.summer.ner

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import org.robolectric.RuntimeEnvironment
import com.summer.ner.banking.BankAccountOrganizer
import com.summer.ner.banking.BankingRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
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
class NerStartupRepairTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val preferences = context.getSharedPreferences("repair_test", Context.MODE_PRIVATE)
    private lateinit var fixture: BankingTestDatabase

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
        fixture = BankingTestDatabase(context)
    }

    @After
    fun tearDown() = fixture.close()

    @Test
    fun repairsOnlyEligibleFailuresOnceAndPreservesCompletedCorrections() = runBlocking {
        val failed = fixture.insertRun(status = "FAILED", priority = "REALTIME")
        fixture.execute("UPDATE ner_runs SET attempts = 3 WHERE id = $failed")
        fixture.dao.updateNotificationState(failed, 10, "READY", null, 1)
        val completed = fixture.insertRun()
        fixture.complete(completed)
        val repository = BankingRepository(fixture.dao, BankAccountOrganizer(context, fixture.dao))
        repository.organizeAccounts()
        repository.saveOverride(completed, "Corrected Store", "120", "INR", "DEBIT", "Shopping", "UPI")
        val link = fixture.string("SELECT account_id FROM sms_transaction_account_links WHERE extraction_id = $completed")
        val oldPipeline = fixture.insertRun(status = "FAILED", fingerprint = "older-pipeline")
        val nonBank = fixture.insertRun(status = "FAILED", classificationId = 7)
        val inactive = fixture.insertRun(status = "FAILED")
        fixture.execute("UPDATE ner_runs SET active = 0 WHERE id = $inactive")
        // Simulate the pre-fix persisted projection with an unparseable amount.
        fixture.execute("UPDATE banking_transaction_facts SET amount = 'invalid', review_state = 'AI_EXTRACTED' WHERE run_id = $completed")

        var enqueues = 0
        val repair = NerStartupRepair(fixture.dao, preferences) {
            assertFalse(preferences.getBoolean(NerStartupRepair.COMPLETED_KEY, false))
            assertEquals("PENDING", fixture.string("SELECT status FROM ner_runs WHERE id = $failed"))
            enqueues++
        }
        repair.run()

        assertEquals("BACKFILL", fixture.string("SELECT priority FROM ner_runs WHERE id = $failed"))
        assertEquals("0", fixture.string("SELECT attempts FROM ner_runs WHERE id = $failed"))
        assertEquals("NONE", fixture.string("SELECT state FROM banking_ner_notifications WHERE run_id = $failed"))
        assertEquals("COMPLETED", fixture.string("SELECT status FROM ner_runs WHERE id = $completed"))
        assertEquals(link, fixture.string("SELECT account_id FROM sms_transaction_account_links WHERE extraction_id = $completed"))
        assertEquals("NEEDS_REVIEW", fixture.string("SELECT review_state FROM banking_transaction_facts WHERE run_id = $completed"))
        assertNull(fixture.string("SELECT amount FROM banking_transaction_facts WHERE run_id = $completed"))
        val corrected = repository.transactionById(completed)!!
        assertEquals("Corrected Store", corrected.merchant)
        assertEquals("120", corrected.amount)
        assertEquals("CONFIRMED", corrected.reviewState)
        listOf(oldPipeline, nonBank, inactive).forEach {
            assertEquals("FAILED", fixture.string("SELECT status FROM ner_runs WHERE id = $it"))
        }

        fixture.dao.markTerminalFailure(failed, 2, "STILL_FAILING", null)
        repair.run()
        assertEquals(1, enqueues)
        assertEquals("FAILED", fixture.string("SELECT status FROM ner_runs WHERE id = $failed"))
    }

    @Test
    fun failedDurableSchedulingDoesNotCommitMarkerAndCanRetry() = runBlocking {
        val run = fixture.insertRun(status = "FAILED")
        try {
            NerStartupRepair(fixture.dao, preferences) { throw IOException("Scheduling unavailable") }.run()
            fail("Expected scheduling failure")
        } catch (_: IOException) { }
        assertFalse(preferences.getBoolean(NerStartupRepair.COMPLETED_KEY, false))
        assertEquals("PENDING", fixture.string("SELECT status FROM ner_runs WHERE id = $run"))
        NerStartupRepair(fixture.dao, preferences) { }.run()
        assertTrue(preferences.getBoolean(NerStartupRepair.COMPLETED_KEY, false))
    }

    @Test
    fun cancellationBeforeSchedulingLeavesRepairRetryable() = runBlocking {
        fixture.insertRun(status = "FAILED")
        try {
            NerStartupRepair(fixture.dao, preferences) { throw CancellationException("Stopped") }.run()
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        assertFalse(preferences.getBoolean(NerStartupRepair.COMPLETED_KEY, false))
    }

    @Test
    fun failedMarkerCommitDoesNotLeaveInMemoryCompletionSet() = runBlocking {
        val failingPreferences = object : SharedPreferences by preferences {
            override fun edit(): SharedPreferences.Editor {
                val editor = preferences.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                        editor.putBoolean(key, value)
                        return this
                    }
                    override fun commit(): Boolean {
                        editor.commit()
                        return false
                    }
                }
            }
        }
        try {
            NerStartupRepair(fixture.dao, failingPreferences) { }.run()
            fail("Expected marker persistence failure")
        } catch (_: IllegalStateException) { }
        assertFalse(preferences.getBoolean(NerStartupRepair.COMPLETED_KEY, false))
    }

    @Test
    fun keepsRemoteDiagnosticCodeInsteadOfWrapperClassName() {
        assertEquals("UnsatisfiedLinkError", NerRemoteException("UnsatisfiedLinkError", null).nerFailureCode())
        assertEquals("IllegalStateException", IllegalStateException().nerFailureCode())
    }
}
