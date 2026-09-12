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
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.summer.core.data.local.db.SmsDatabase
import com.summer.core.data.local.entities.SenderAddressEntity
import com.summer.core.data.local.entities.SenderType
import com.summer.core.data.local.entities.SmsEntity
import com.summer.core.data.local.entities.NerMentionEntity
import com.summer.core.data.local.entities.NerRunEntity
import com.summer.core.data.local.preference.PreferenceKey
import com.summer.notifai.ui.MainActivity
import com.summer.notifai.di.NotificationIntentProviderImpl
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BankingTransactionUiTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val database by lazy { SmsDatabase.getDatabase(context) }
    private var extractionId: Long = 0
    private var previousDefaultSmsPromptTime: Long = 0

    @Before
    fun insertFixture() = runBlocking {
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
                smsClassificationTypeId = 2,
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

    @After
    fun cleanUp() = runBlocking {
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
        waitForText("HDFC Bank account")
        onView(withText("HDFC Bank account")).perform(click())
        onView(withText(R.string.last_reported_balance)).check(matches(isDisplayed()))
        onView(withText("Synthetic Store")).check(matches(isDisplayed()))
        scenario.onActivity { activity ->
            assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
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
                onView(withText(text)).check(matches(isDisplayed()))
                return
            } catch (_: NoMatchingViewException) {
                Thread.sleep(100)
            }
        }
        onView(withText(text)).check(matches(isDisplayed()))
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
    }

    companion object {
        private const val FIXTURE_SENDER = "LOCALUITESTBANK"
    }
}
