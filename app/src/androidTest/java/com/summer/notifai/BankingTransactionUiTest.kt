package com.summer.notifai

import android.content.Intent
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import com.summer.notifai.ui.banking.account.AccountDetailViewModel
import com.summer.core.data.local.entities.BankAccountEntity
import com.summer.core.data.local.entities.BankAccountBalanceObservationEntity
import com.summer.core.data.local.entities.SmsTransactionAccountLinkEntity
import com.summer.core.data.local.db.SmsDatabase
import com.summer.core.data.local.entities.SenderAddressEntity
import com.summer.core.data.local.entities.SenderType
import com.summer.core.data.local.entities.SmsEntity
import com.summer.core.data.local.entities.NerMentionEntity
import com.summer.core.data.local.entities.NerRunEntity
import com.summer.core.data.local.preference.PreferenceKey
import com.summer.notifai.ui.MainActivity
import com.summer.notifai.di.NotificationIntentProviderImpl
import com.summer.ner.NerServiceClient
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.hamcrest.Matchers.allOf

@RunWith(AndroidJUnit4::class)
class BankingTransactionUiTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val database by lazy { SmsDatabase.getDatabase(context) }
    private var extractionId: Long = 0
    private var fixtureAccountId: Long = 0
    private var previousDefaultSmsPromptTime: Long = 0

    @Before
    fun insertFixture() = runBlocking {
        finishActivities()
        val preferences = context.getSharedPreferences(PreferenceKey.DEFAULT_PREF, android.content.Context.MODE_PRIVATE)
        previousDefaultSmsPromptTime = preferences.getLong(PreferenceKey.DEFAULT_SMS_PROMPT_LAST_SHOWN.key, 0)
        preferences.edit()
            .putLong(PreferenceKey.DEFAULT_SMS_PROMPT_LAST_SHOWN.key, System.currentTimeMillis())
            .commit()
        cleanFixture()
        val senderId = database.smsDao().insertSenderAddress(
            SenderAddressEntity(
                senderAddress = FIXTURE_SENDER,
                originalSenderAddress = FIXTURE_SENDER,
                senderType = SenderType.BUSINESS,
            )
        )
        val smsId = database.smsDao().insertSmsMessage(
            SmsEntity(
                androidSmsId = null,
                senderAddressId = senderId,
                rawAddress = FIXTURE_SENDER,
                body = "Synthetic local-only transaction fixture",
                date = 1_700_000_000_000,
                dateSent = null,
                type = 1,
                threadId = null,
                read = 0,
                status = null,
                serviceCenter = null,
                subscriptionId = null,
                // These tests supply extraction results themselves. Exclude fixtures from
                // production backfill so it cannot supersede the seeded run during UI assertions.
                smsClassificationTypeId = null,
                importanceScore = 1,
                confidenceScore = 1f,
                createdAtApp = 1,
                updatedAtApp = 1,
            )
        )
        extractionId = database.nerDao().insertRun(
            NerRunEntity(
                smsId = smsId,
                status = "PENDING",
                priority = "REALTIME",
                pipelineFingerprint = "synthetic-ui-test",
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
                entity(extractionId, 3, "ACCOUNT", "0012345678", "0012345678"),
                entity(extractionId, 4, "BANK", "HDFC Bank"),
                entity(extractionId, 5, "BALANCE", "INR 2200", "2200"),
            ),
            modelId = "synthetic-ui-test",
            modelSha256 = "synthetic-model-hash",
            tokenizerSha256 = "synthetic-tokenizer-hash",
            preprocessingVersion = "synthetic-v1",
            tokenCount = 8,
            truncated = false,
            inferenceMs = 1.0,
            notificationState = "NONE",
            now = 2,
            pipelineFingerprint = "synthetic-ui-test",
        )
        // Own the account fixture as well as the SMS. Real inference can otherwise leave
        // orphan account rows between tests, or collide with unrelated account names.
        fixtureAccountId = insertAccount(FIXTURE_ACCOUNT, FIXTURE_ACCOUNT_NAME)
        database.nerDao().upsertResolvedAccountLink(SmsTransactionAccountLinkEntity(
            extractionId = extractionId, accountId = fixtureAccountId, source = "MANUAL",
            confidence = 1.0, reason = "SYNTHETIC_UI_TEST", createdAt = 1, updatedAt = 1,
        ))
        database.nerDao().upsertResolvedBalanceObservation(BankAccountBalanceObservationEntity(
            accountId = fixtureAccountId, sourceExtractionId = extractionId, balance = "2200",
            currency = "INR", observedAt = 1_700_000_000_000, createdAt = 1,
        ))
        assertNotNull(database.nerDao().transactionById(extractionId))
    }

    @Test
    fun bankingNotificationPendingIntentOpensExactSecureDetail() {
        NotificationIntentProviderImpl(context)
            .provideBankingTransactionPendingIntent(extractionId)
            .send()

        onView(withText("Synthetic Store")).check(matches(isDisplayed()))
        onView(withText("-₹125")).check(matches(isDisplayed()))
    }

    @Test
    fun nativeInferencePersistsEntitiesAndOpensTransactionHistory(): Unit = runBlocking {
        val body = "INR 125.00 debited from HDFC Bank A/c XX123456 at TEST STORE. Avl bal INR 2200.00. Ref 123456789."
        val smsId = database.nerDao().transactionById(extractionId)!!.smsId
        database.openHelper.writableDatabase.execSQL("UPDATE sms_messages SET body = ? WHERE id = ?", arrayOf(body, smsId))
        val result = NerServiceClient(context).use { client ->
            withTimeout(120_000) { client.extract(smsId, "HDFCBK", body) }
        }
        assertTrue(result.entities.any { it.type == "AMOUNT" })
        result.entities.forEach {
            assertTrue(it.startOffset >= 0 && it.endOffset <= body.length)
            org.junit.Assert.assertEquals(it.rawText, body.substring(it.startOffset, it.endOffset))
        }
        database.nerDao().complete(
            extractionId,
            result.entities.mapIndexed { index, entity ->
                NerMentionEntity(runId = extractionId, entityOrder = index, entityType = entity.type,
                    rawText = entity.rawText, normalizedValue = entity.normalizedValue,
                    startOffset = entity.startOffset, endOffset = entity.endOffset)
            },
            result.modelId, result.modelSha256, result.tokenizerSha256, result.preprocessingVersion,
            result.tokenCount, result.truncated, result.inferenceMs, "NONE", System.currentTimeMillis(),
            result.pipelineFingerprint, result.labelSchemaSha256, result.decoderVersion, result.normalizerVersion,
        )
        val transaction = database.nerDao().transactionById(extractionId)!!
        assertNotNull(transaction.amount)
        NotificationIntentProviderImpl(context).provideBankingTransactionPendingIntent(extractionId).send()
        waitForText(transaction.merchant ?: transaction.bank ?: "Bank transaction")
        onView(withId(R.id.original_sms)).check(matches(withText(body)))
    }

    @Test
    fun failedAnalysisShowsFailureInsteadOfEndlessPreparing() = runBlocking {
        database.nerDao().markTerminalFailure(extractionId, System.currentTimeMillis(), "TEST_FAILURE", null)
        val scenario = ActivityScenario.launch<MainActivity>(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        scenario.onActivity { navigateToTransactions(it) }
        waitForText(context.resources.getQuantityString(R.plurals.banking_analysis_failed, 1, 1))
        scenario.close()
    }

    @After
    fun cleanUp() = runBlocking {
        finishActivities()
        cleanFixture()
        context.getSharedPreferences(PreferenceKey.DEFAULT_PREF, android.content.Context.MODE_PRIVATE)
            .edit()
            .putLong(PreferenceKey.DEFAULT_SMS_PROMPT_LAST_SHOWN.key, previousDefaultSmsPromptTime)
            .commit()
        Unit
    }

    @Test
    fun listDetailAndEditFlowUsesSecureWindowAndPersistsOverride() {
        val scenario = ActivityScenario.launch<MainActivity>(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        scenario.onActivity { activity -> navigateToTransactions(activity) }

        waitForText("Synthetic Store")
        onView(withText("Synthetic Store")).check(matches(isDisplayed())).perform(click())
        onView(withText("-₹125")).check(matches(isDisplayed()))
        scenario.onActivity { activity ->
            assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        }

        onView(withText(R.string.edit)).perform(click())
        onView(withId(R.id.merchant)).perform(replaceText("Corrected Store"))
        onView(withId(R.id.amount)).perform(replaceText("150"))
        onView(withId(R.id.currency)).perform(replaceText("INR"))
        closeSoftKeyboard()
        onView(withText(R.string.income)).perform(click())
        onView(withText("Shopping")).perform(click())
        onView(withText("Card")).perform(click())
        onView(withText(R.string.confirm_and_save)).perform(click())

        onView(withText("Corrected Store")).check(matches(isDisplayed()))
        onView(withText("+₹150")).check(matches(isDisplayed()))
        onView(withText(R.string.confirmed)).check(matches(isDisplayed()))
        scenario.close()
    }

    @Test
    fun accountFirstTabsOpenSecureAccountHistory() {
        val scenario = ActivityScenario.launch<MainActivity>(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        scenario.onActivity { activity -> navigateToTransactions(activity) }

        onView(withId(R.id.banking_accounts)).perform(click())
        waitForText(FIXTURE_ACCOUNT_NAME)
        onView(allOf(withText(FIXTURE_ACCOUNT_NAME), isDisplayed())).perform(click())
        onView(withText(R.string.last_reported_balance)).check(matches(isDisplayed()))
        onView(withText("Synthetic Store")).check(matches(isDisplayed()))
        scenario.onActivity { activity ->
            assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        }
        scenario.close()
    }

    @Test
    fun mergeAndAssignmentDialogsExposeTargetsAfterLifecycleRestart() = runBlocking {
        val targetId = insertAccount(FIXTURE_TARGET, "Synthetic target account")
        insertAdditionalTransaction(targetId, "Synthetic target transaction")
        insertAdditionalTransaction(fixtureAccountId, "Additional source transaction")
        val scenario = ActivityScenario.launch<MainActivity>(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        scenario.onActivity { navigateToTransactions(it) }
        onView(withId(R.id.banking_accounts)).perform(click())
        waitForText(FIXTURE_ACCOUNT_NAME)
        onView(allOf(withText(FIXTURE_ACCOUNT_NAME), isDisplayed())).perform(click())
        scenario.moveToState(Lifecycle.State.CREATED).moveToState(Lifecycle.State.RESUMED)
        withTimeout(10_000) {
            var ready = false
            while (!ready) {
                scenario.onActivity { activity ->
                    val host = activity.supportFragmentManager.findFragmentById(R.id.fcv_main_navHost) as NavHostFragment
                    val fragment = host.childFragmentManager.primaryNavigationFragment!!
                    val model = ViewModelProvider(fragment)[AccountDetailViewModel::class.java]
                    ready = model.account.value != null && model.allAccounts.value.any { it.id == targetId }
                    if (ready) {
                        val current = model.account.value!!
                        assertTrue(model.allAccounts.value.any {
                            it.id == targetId && it.id != current.id &&
                                it.canonicalBank == current.canonicalBank && it.instrumentType == current.instrumentType
                        })
                    }
                }
                if (!ready) kotlinx.coroutines.delay(50)
            }
        }
        onView(withId(R.id.merge)).perform(object : androidx.test.espresso.ViewAction {
            override fun getDescription() = "Wait for the resumed window's input transition"
            override fun getConstraints() = isDisplayed()
            override fun perform(controller: androidx.test.espresso.UiController, view: android.view.View) {
                controller.loopMainThreadForAtLeast(600)
            }
        }, click())
        waitForText("Synthetic target account")
        androidx.test.espresso.Espresso.pressBack()
        onView(withText("Synthetic Store")).perform(longClick())
        waitForText("Synthetic target account")
        onView(withText("Synthetic target account")).perform(click())
        withTimeout(10_000) {
            while (database.nerDao().transactionById(extractionId)?.accountId != targetId) {
                kotlinx.coroutines.delay(50)
            }
        }
        // The merge dialog must still work after reassignment updates the source account.
        onView(withId(R.id.merge)).perform(click())
        waitForText("Synthetic target account")
        onView(withText("Synthetic target account")).perform(click())
        withTimeout(10_000) {
            while (database.nerDao().bankAccountById(fixtureAccountId)?.mergeTargetId != targetId) {
                kotlinx.coroutines.delay(50)
            }
        }
        scenario.close()
    }

    private fun navigateToTransactions(activity: FragmentActivity) {
        val host = activity.supportFragmentManager
            .findFragmentById(R.id.fcv_main_navHost) as NavHostFragment
        host.navController.navigate(R.id.action_global_to_transaction_list)
    }

    private fun waitForText(text: String) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(allOf(withText(text), isDisplayed())).check(matches(isDisplayed()))
                return
            } catch (_: NoMatchingViewException) {
                Thread.sleep(100)
            }
        }
        onView(allOf(withText(text), isDisplayed())).check(matches(isDisplayed()))
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

    private fun cleanFixture() {
        database.openHelper.writableDatabase.execSQL(
            "DELETE FROM sms_messages WHERE raw_address = ?",
            arrayOf(FIXTURE_SENDER),
        )
        database.openHelper.writableDatabase.execSQL(
            "DELETE FROM sender_addresses WHERE sender_address = ?",
            arrayOf(FIXTURE_SENDER),
        )
        database.openHelper.writableDatabase.execSQL(
            "DELETE FROM bank_accounts WHERE identifier_fingerprint IN (?, ?)",
            arrayOf(FIXTURE_ACCOUNT, FIXTURE_TARGET),
        )
    }

    private suspend fun insertAccount(fingerprint: String, name: String): Long =
        database.nerDao().insertBankAccount(BankAccountEntity(
            canonicalBank = "hdfc", instrumentType = "ACCOUNT", generatedName = name,
            maskedIdentifier = "••••5678", identifierFingerprint = fingerprint, logoKey = null,
            createdAt = 1, updatedAt = 1,
        ))

    private suspend fun insertAdditionalTransaction(accountId: Long, merchant: String) {
        val sourceSmsId = database.nerDao().transactionById(extractionId)!!.smsId
        val source = database.smsDao().getSmsEntityById(sourceSmsId)!!
        val smsId = database.smsDao().insertSmsMessage(source.copy(id = 0, body = "Synthetic fixture: $merchant"))
        val run = database.nerDao().insertRun(NerRunEntity(
            smsId = smsId, status = "PENDING", priority = "BACKFILL", pipelineFingerprint = "synthetic-ui-test",
            createdAt = 1, updatedAt = 1,
        ))
        database.nerDao().complete(run, listOf(
            entity(run, 0, "MERCHANT", merchant), entity(run, 1, "AMOUNT", "INR 10", "10"),
            entity(run, 2, "DIRECTION", "debited", "DEBIT"),
        ), "synthetic-ui-test", "model", "tokenizer", "preprocessor", 8, false, 1.0, "NONE", 2,
            "synthetic-ui-test")
        database.nerDao().upsertResolvedAccountLink(SmsTransactionAccountLinkEntity(
            extractionId = run, accountId = accountId, source = "MANUAL", confidence = 1.0,
            reason = "SYNTHETIC_UI_TEST", createdAt = 1, updatedAt = 1,
        ))
    }

    private fun finishActivities() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            Stage.values().flatMap { monitor.getActivitiesInStage(it) }.distinct().forEach { it.finish() }
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    companion object {
        private const val FIXTURE_SENDER = "LOCALUITESTBANK"
        private const val FIXTURE_ACCOUNT = "local-ui-fixture-account"
        private const val FIXTURE_TARGET = "local-ui-fixture-target"
        private const val FIXTURE_ACCOUNT_NAME = "Synthetic UI account"
    }
}
