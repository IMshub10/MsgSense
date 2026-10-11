package com.summer.ner.banking

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.summer.core.data.local.db.SmsDatabase
import com.summer.core.data.local.entities.SenderAddressEntity
import com.summer.core.data.local.entities.SenderType
import com.summer.core.data.local.entities.SmsEntity
import com.summer.core.data.local.entities.NerMentionEntity
import com.summer.core.data.local.entities.NerRunEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BankingRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(context, SmsDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val repository = BankingRepository(
        database.nerDao(),
        BankAccountOrganizer(context, database.nerDao()),
    )

    @After
    fun close() = database.close()

    @Test
    fun completedExtractionIsProjectedAndOverrideIsApplied() = runBlocking {
        val extractionId = insertSyntheticTransaction()

        val initial = repository.observeTransactions().first().single()
        assertEquals("Synthetic Store", initial.merchant)
        assertEquals("-₹125", initial.signedAmount)
        assertEquals("", initial.originalSms)

        repository.saveOverride(
            extractionId = extractionId,
            merchant = "Corrected Store",
            amount = "150",
            currency = "INR",
            direction = "CREDIT",
            category = "Shopping",
            paymentMethod = "Card",
        )

        val updated = repository.observeTransaction(extractionId).first()!!
        assertEquals("Corrected Store", updated.merchant)
        assertEquals("+₹150", updated.signedAmount)
        assertEquals("CONFIRMED", updated.reviewState)
        assertEquals("Synthetic transaction fixture", updated.originalSms)
    }

    @Test
    fun blockedSenderIsNeverExposed() = runBlocking {
        insertSyntheticTransaction(blocked = true)
        assertTrue(repository.observeTransactions().first().isEmpty())
    }

    @Test
    fun organizerCreatesMaskedAccountAndReportedBalance() = runBlocking {
        insertSyntheticTransaction()
        repository.organizeAccounts()

        val account = repository.observeAccounts().first().single()
        assertEquals("hdfc", account.canonicalBank)
        assertEquals("••••5678", account.maskedIdentifier)
        assertEquals("2200", account.lastReportedBalance)
        assertEquals(1, account.transactions.size)
    }

    private suspend fun insertSyntheticTransaction(blocked: Boolean = false): Long {
        val senderId = database.smsDao().insertSenderAddress(
            SenderAddressEntity(
                senderAddress = "TESTBANK",
                originalSenderAddress = "TESTBANK",
                senderType = SenderType.BUSINESS,
                isBlocked = blocked,
            )
        )
        val smsId = database.smsDao().insertSmsMessage(
            SmsEntity(
                androidSmsId = null,
                senderAddressId = senderId,
                rawAddress = "TESTBANK",
                body = "Synthetic transaction fixture",
                date = 1_700_000_000_000,
                dateSent = null,
                type = 1,
                threadId = null,
                read = 0,
                status = null,
                serviceCenter = null,
                subscriptionId = null,
                smsClassificationTypeId = 2,
                importanceScore = 1,
                confidenceScore = 1f,
                createdAtApp = 1,
                updatedAtApp = 1,
            )
        )
        val extractionId = database.nerDao().insertRun(
            NerRunEntity(
                smsId = smsId,
                status = "COMPLETED",
                priority = "REALTIME",
                pipelineFingerprint = "synthetic",
                createdAt = 1,
                updatedAt = 1,
            )
        )
        database.nerDao().complete(
            runId = extractionId,
            mentions = listOf(
                entity(extractionId, 0, "MERCHANT", "Synthetic Store"),
                entity(extractionId, 1, "AMOUNT", "INR 125", "125"),
                entity(extractionId, 2, "DIRECTION", "debited", "DEBIT"),
                entity(extractionId, 3, "BANK", "HDFC Bank"),
                entity(extractionId, 4, "ACCOUNT", "0012345678", "0012345678"),
                entity(extractionId, 5, "BALANCE", "INR 2200", "2200"),
            ),
            modelId = "synthetic",
            modelSha256 = "synthetic-model",
            tokenizerSha256 = "synthetic-tokenizer",
            preprocessingVersion = "synthetic-preprocessor",
            tokenCount = 8,
            truncated = false,
            inferenceMs = 1.0,
            notificationState = "NONE",
            now = 1,
            pipelineFingerprint = "synthetic",
        )
        return extractionId
    }

    private fun entity(
        extractionId: Long,
        order: Int,
        type: String,
        raw: String,
        normalized: String? = raw,
    ) = NerMentionEntity(
        runId = extractionId,
        entityOrder = order,
        entityType = type,
        rawText = raw,
        normalizedValue = normalized,
        startOffset = 0,
        endOffset = raw.length,
    )
}
