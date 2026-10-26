package com.summer.notifai.ui.banking.account

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import org.robolectric.RuntimeEnvironment
import com.summer.core.data.local.db.SmsDatabase
import com.summer.core.data.local.entities.NerMentionEntity
import com.summer.core.data.local.entities.NerRunEntity
import com.summer.core.data.local.entities.SenderAddressEntity
import com.summer.core.data.local.entities.SenderType
import com.summer.core.data.local.entities.SmsEntity
import com.summer.ner.banking.BankAccountOrganizer
import com.summer.ner.banking.BankingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AccountDetailViewModelTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val database = Room.inMemoryDatabaseBuilder(context, SmsDatabase::class.java).allowMainThreadQueries().build()
    private val repository = BankingRepository(database.nerDao(), BankAccountOrganizer(context, database.nerDao()))
    private val store = ViewModelStore()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() {
        store.clear()
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun choicesLoadAndUpdateWithoutACollectorSoDialogsCanReadThem() = runBlocking {
        val sender = database.smsDao().insertSenderAddress(
            SenderAddressEntity(senderAddress = "TESTBANK", originalSenderAddress = "TESTBANK", senderType = SenderType.BUSINESS)
        )
        val first = insertTransaction(sender, "0011111111")
        val second = insertTransaction(sender, "0022222222")
        repository.organizeAccounts()
        val firstAccount = repository.transactionById(first)!!.accountId!!
        val secondAccount = repository.transactionById(second)!!.accountId!!
        val model = AccountDetailViewModel(SavedStateHandle(mapOf("accountId" to firstAccount)), repository)
        store.put("account", model)

        // Intentionally read .value only, just as the merge/assignment dialogs do.
        awaitValue { model.allAccounts.value.size == 2 }
        assertTrue(model.allAccounts.value.any { it.id == secondAccount })
        repository.renameAccount(secondAccount, "Merge target")
        awaitValue { model.allAccounts.value.any { it.id == secondAccount && it.name == "Merge target" } }
        repository.assignTransaction(first, secondAccount)
        assertEquals(secondAccount, repository.transactionById(first)!!.accountId)
    }

    private suspend fun awaitValue(condition: () -> Boolean) = withContext(Dispatchers.IO) {
        withTimeout(5_000) { while (!condition()) delay(10) }
    }

    private suspend fun insertTransaction(sender: Long, account: String): Long {
        val sms = database.smsDao().insertSmsMessage(SmsEntity(
            androidSmsId = null, senderAddressId = sender, rawAddress = "TESTBANK", body = "Synthetic fixture",
            date = 1, dateSent = null, type = 1, threadId = null, read = 0, status = null,
            serviceCenter = null, subscriptionId = null, smsClassificationTypeId = 2, importanceScore = 1,
            confidenceScore = 1f, createdAtApp = 1, updatedAtApp = 1,
        ))
        val run = database.nerDao().insertRun(NerRunEntity(smsId = sms, status = "PENDING", priority = "BACKFILL",
            pipelineFingerprint = "test", createdAt = 1, updatedAt = 1))
        val mentions = listOf(
            Triple("AMOUNT", "INR 100", "100"), Triple("DIRECTION", "debited", "DEBIT"),
            Triple("BANK", "HDFC Bank", "HDFC Bank"), Triple("ACCOUNT", account, account),
        ).mapIndexed { index, (type, raw, normalized) ->
            NerMentionEntity(runId = run, entityOrder = index, entityType = type, rawText = raw,
                normalizedValue = normalized, startOffset = 0, endOffset = raw.length)
        }
        database.nerDao().complete(run, mentions, "test", "model", "tokenizer", "preprocessor", 8, false,
            1.0, "NONE", 1, "test")
        return run
    }
}
