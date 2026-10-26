package com.summer.ner

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.summer.core.android.notification.AppNotificationManager
import com.summer.core.android.notification.NotificationIntentProvider
import com.summer.core.di.ChatSessionTracker
import com.summer.core.ui.model.SmsImportanceType
import com.summer.ner.banking.BankAccountOrganizer
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class NerBackfillContinuityTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private lateinit var fixture: BankingTestDatabase
    private lateinit var scheduler: NerWorkScheduler
    private lateinit var workManager: WorkManager
    private lateinit var workerFactory: WorkerFactory
    private val extractions = AtomicInteger()
    private val clientsClosed = AtomicInteger()
    private var cancelExtraction = false

    @Before
    fun setUp() {
        fixture = BankingTestDatabase(context)
        scheduler = NerWorkScheduler(context, fixture.dao)
        val notifications = AppNotificationManager(context, object : NotificationIntentProvider {
            private fun pending() = PendingIntent.getActivity(context, 0, Intent("test"), PendingIntent.FLAG_IMMUTABLE)
            override fun provideSummaryPendingIntent() = pending()
            override fun provideSmsInboxPendingIntent(senderAddressId: Long, smsImportanceType: SmsImportanceType) = pending()
            override fun provideSmsProcessingPendingIntent() = pending()
            override fun provideAppHomePendingIntent() = pending()
            override fun provideBankingTransactionPendingIntent(extractionId: Long) = pending()
        }, ChatSessionTracker())
        val clients = NerClientFactory {
            object : NerExtractionClient {
                override suspend fun extract(smsId: Long, sender: String, body: String): NerInferenceResult {
                    extractions.incrementAndGet()
                    if (cancelExtraction) throw CancellationException("Test interruption")
                    return NerInferenceResult(smsId, "test", "model", "tokenizer", "preprocessor",
                        NerPipelineMetadata.PIPELINE_FINGERPRINT, "labels", "decoder", "normalizer", 8, false, 1.0,
                        listOf(NerExtractedEntity("AMOUNT", "INR 100", "100", 0, 7),
                            NerExtractedEntity("DIRECTION", "debited", "DEBIT", 8, 15)))
                }
                override fun close() { clientsClosed.incrementAndGet() }
            }
        }
        workerFactory = object : WorkerFactory() {
            override fun createWorker(context: Context, name: String, parameters: WorkerParameters): ListenableWorker =
                if (name == NerCoordinatorWorker::class.java.name &&
                    parameters.inputData.getString(NerCoordinatorWorker.INPUT_MODE) == NerWorkScheduler.MODE_BACKFILL) {
                    NerCoordinatorWorker(context, parameters, fixture.dao, scheduler, notifications,
                        BankAccountOrganizer(context, fixture.dao), clients)
                } else HoldingWorker(context, parameters)
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder()
            .setExecutor(SynchronousExecutor()).setTaskExecutor(SynchronousExecutor())
            .setWorkerFactory(workerFactory).build())
        workManager = WorkManager.getInstance(context)
    }

    @After
    fun tearDown() {
        workManager.cancelAllWork().result.get()
        WorkManagerTestInitHelper.closeWorkDatabase()
        fixture.close()
    }

    @Test
    fun resumesAfterRealtimeSuccessAndDeduplicatesExternalTriggers() = runBlocking {
        assertPreemptionResumes(terminalFailure = false)
    }

    @Test
    fun resumesAfterRealtimeTerminalFailure() = runBlocking {
        assertPreemptionResumes(terminalFailure = true)
    }

    @Test
    fun cancellationClosesClientAndInterruptedRowCanBeRecovered() = runBlocking {
        val run = fixture.insertRun()
        cancelExtraction = true
        try {
            directWorker().doWork()
            fail("Expected interruption")
        } catch (_: CancellationException) { }
        assertEquals(1, clientsClosed.get())
        assertEquals("RUNNING", fixture.string("SELECT status FROM ner_runs WHERE id = $run"))
        // The next coordinator recovers RUNNING rows, without recreating the extraction.
        cancelExtraction = false
        directWorker().doWork()
        assertEquals("COMPLETED", fixture.string("SELECT status FROM ner_runs WHERE id = $run"))
        assertEquals("1", fixture.string("SELECT COUNT(*) FROM ner_runs"))
    }

    @Test
    fun explicitlyStoppedWorkerDoesNotStartInferenceOrRestartCancelledDrain() = runBlocking {
        val run = fixture.insertRun()
        val worker = directWorker()
        worker.stop(WorkInfo.STOP_REASON_CANCELLED_BY_APP)
        worker.doWork()
        assertEquals(0, extractions.get())
        assertEquals("PENDING", fixture.string("SELECT status FROM ner_runs WHERE id = $run"))
        assertTrue(workManager.getWorkInfosForUniqueWork(BACKFILL_WORK).get().isEmpty())
    }

    private suspend fun assertPreemptionResumes(terminalFailure: Boolean) {
        val historical = fixture.insertRun()
        val realtime = fixture.insertRun(priority = "REALTIME")
        scheduler.enqueueBackfill()
        await { backfillWork().any { it.state == WorkInfo.State.SUCCEEDED } &&
            backfillWork().count { !it.state.isFinished } == 1 }
        assertEquals(0, extractions.get())
        assertEquals("PENDING", fixture.string("SELECT status FROM ner_runs WHERE id = $historical"))
        val continuation = backfillWork().single { !it.state.isFinished }
        assertEquals(NerWorkScheduler.REALTIME_YIELD_MS, continuation.initialDelayMillis)
        val initialCount = backfillWork().size
        repeat(3) { scheduler.enqueueBackfill() }
        // Flush the serial task executor through a query after the enqueue operations.
        assertEquals(initialCount, backfillWork().size)

        if (terminalFailure) fixture.dao.markTerminalFailure(realtime, 2, "TEST_FAILURE", null)
        else fixture.complete(realtime)
        WorkManagerTestInitHelper.getTestDriver(context)!!.setInitialDelayMet(continuation.id)
        await { fixture.string("SELECT status FROM ner_runs WHERE id = $historical") == "COMPLETED" &&
            backfillWork().all { it.state.isFinished } }
        assertEquals(1, extractions.get())
        assertEquals(1, clientsClosed.get())
        assertEquals("NONE", fixture.string("SELECT state FROM banking_ner_notifications WHERE run_id = $historical"))
    }

    private fun directWorker(): NerCoordinatorWorker = TestListenableWorkerBuilder
        .from(context, NerCoordinatorWorker::class.java)
        .setWorkerFactory(workerFactory)
        .setInputData(Data.Builder().putString(NerCoordinatorWorker.INPUT_MODE, NerWorkScheduler.MODE_BACKFILL).build())
        .build()

    private fun backfillWork() = workManager.getWorkInfosForUniqueWork(BACKFILL_WORK).get()

    private suspend fun await(condition: () -> Boolean) = withContext(Dispatchers.IO) {
        withTimeout(10_000) { while (!condition()) delay(10) }
    }

    private class HoldingWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
        override suspend fun doWork(): Result = awaitCancellation()
    }

    companion object {
        private const val BACKFILL_WORK = "production_mobilebert_ner_backfill"
    }
}
