package com.summer.ner

import android.content.Context
import androidx.room.Room
import com.summer.core.data.local.db.SmsDatabase
import com.summer.core.data.local.entities.NerMentionEntity
import com.summer.core.data.local.entities.NerRunEntity
import com.summer.core.data.local.entities.SenderAddressEntity
import com.summer.core.data.local.entities.SenderType
import com.summer.core.data.local.entities.SmsEntity

internal class BankingTestDatabase(context: Context) : AutoCloseable {
    val database = Room.inMemoryDatabaseBuilder(context, SmsDatabase::class.java).allowMainThreadQueries().build()
    val dao = database.nerDao()
    private var senderId: Long? = null
    private var sequence = 0L

    suspend fun insertRun(
        status: String = "PENDING",
        priority: String = "BACKFILL",
        fingerprint: String = NerPipelineMetadata.PIPELINE_FINGERPRINT,
        classificationId: Int = 2,
    ): Long {
        val sender = senderId ?: database.smsDao().insertSenderAddress(
            SenderAddressEntity(senderAddress = "TESTHDFC", originalSenderAddress = "TESTHDFC", senderType = SenderType.BUSINESS)
        ).also { senderId = it }
        val sms = database.smsDao().insertSmsMessage(
            SmsEntity(
                androidSmsId = null, senderAddressId = sender, rawAddress = "TESTHDFC",
                body = "Synthetic banking test fixture", date = 1_700_000_000_000 + sequence++,
                dateSent = null, type = 1, threadId = null, read = 0, status = null,
                serviceCenter = null, subscriptionId = null, smsClassificationTypeId = classificationId,
                importanceScore = 1, confidenceScore = 1f, createdAtApp = 1, updatedAtApp = 1,
            )
        )
        return dao.insertRun(NerRunEntity(smsId = sms, status = status, priority = priority,
            pipelineFingerprint = fingerprint, createdAt = 1, updatedAt = 1))
    }

    suspend fun complete(runId: Long, account: String = "0012345678", amount: String? = "100", balance: String = "2000") {
        val mentions = listOf(
            "MERCHANT" to ("Test Store" to "Test Store"),
            "AMOUNT" to ("INR 100" to amount),
            "DIRECTION" to ("debited" to "DEBIT"),
            "BANK" to ("HDFC Bank" to "HDFC Bank"),
            "ACCOUNT" to (account to account),
            "BALANCE" to ("INR $balance" to balance),
        ).mapIndexed { index, (type, value) ->
            NerMentionEntity(runId = runId, entityOrder = index, entityType = type, rawText = value.first,
                normalizedValue = value.second, startOffset = 0, endOffset = value.first.length)
        }
        dao.complete(runId, mentions, "synthetic", "model", "tokenizer", "preprocessor", 8, false, 1.0,
            "NONE", 1, NerPipelineMetadata.PIPELINE_FINGERPRINT)
    }

    fun string(sql: String): String? = database.openHelper.writableDatabase.query(sql).use {
        check(it.moveToFirst())
        if (it.isNull(0)) null else it.getString(0)
    }

    fun execute(sql: String) = database.openHelper.writableDatabase.execSQL(sql)
    override fun close() = database.close()
}
