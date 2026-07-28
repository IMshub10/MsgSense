package com.summer.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.summer.core.banking.BankingTransactionFactBuilder
import com.summer.core.data.local.entities.BankAccountBalanceObservationEntity
import com.summer.core.data.local.entities.BankAccountEntity
import com.summer.core.data.local.entities.BankingNerNotificationEntity
import com.summer.core.data.local.entities.BankingTransactionFactEntity
import com.summer.core.data.local.entities.BankingTransactionOverrideEntity
import com.summer.core.data.local.entities.NerMentionEntity
import com.summer.core.data.local.entities.NerRunEntity
import com.summer.core.data.local.entities.NerRunMetadataEntity
import com.summer.core.data.local.entities.SmsTransactionAccountLinkEntity
import com.summer.core.data.local.model.AccountOrganizingCandidate
import com.summer.core.data.local.model.BankingNotificationRow
import com.summer.core.data.local.model.BankingTransactionRow
import com.summer.core.data.local.model.NerProcessingSummary
import com.summer.core.data.local.model.PendingNerSms
import com.summer.core.ner.NerConstants
import com.summer.core.ner.NerEntityTypes
import kotlinx.coroutines.flow.Flow

@Dao
abstract class NerDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertBankAccount(account: BankAccountEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAccountLink(link: SmsTransactionAccountLinkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertBalanceObservation(observation: BankAccountBalanceObservationEntity)

    @Query("SELECT * FROM bank_accounts WHERE identifier_fingerprint = :fingerprint LIMIT 1")
    abstract suspend fun bankAccountByFingerprint(fingerprint: String): BankAccountEntity?

    @Query("SELECT * FROM bank_accounts WHERE merge_target_id IS NULL ORDER BY created_at")
    abstract fun observeBankAccounts(): Flow<List<BankAccountEntity>>

    @Query("SELECT * FROM sms_transaction_account_links")
    abstract fun observeAccountLinks(): Flow<List<SmsTransactionAccountLinkEntity>>

    @Query("SELECT * FROM bank_account_balance_observations ORDER BY observed_at DESC")
    abstract fun observeBalanceObservations(): Flow<List<BankAccountBalanceObservationEntity>>

    @Query("UPDATE bank_accounts SET custom_name = :name, updated_at = :now WHERE id = :accountId")
    abstract suspend fun renameBankAccount(accountId: Long, name: String?, now: Long)

    @Query("UPDATE bank_accounts SET is_hidden = :hidden, updated_at = :now WHERE id = :accountId")
    abstract suspend fun setBankAccountHidden(accountId: Long, hidden: Boolean, now: Long)

    @Query(
        """
        UPDATE sms_transaction_account_links
        SET account_id = :accountId, source = 'USER', confidence = 1.0,
            reason = 'MANUAL_ASSIGNMENT', updated_at = :now
        WHERE extraction_id = :extractionId
        """
    )
    abstract suspend fun reassignTransactionLink(extractionId: Long, accountId: Long, now: Long): Int

    @Query("UPDATE bank_account_balance_observations SET account_id = :accountId WHERE source_extraction_id = :extractionId")
    abstract suspend fun reassignBalanceObservation(extractionId: Long, accountId: Long)

    @Transaction
    open suspend fun reassignTransaction(extractionId: Long, accountId: Long, now: Long) {
        reassignTransactionLink(extractionId, accountId, now)
        reassignBalanceObservation(extractionId, accountId)
    }

    @Query("DELETE FROM sms_transaction_account_links WHERE extraction_id = :extractionId")
    abstract suspend fun unassignTransaction(extractionId: Long)

    @Query(
        """
        UPDATE sms_transaction_account_links SET account_id = :targetId, source = 'USER',
            confidence = 1.0, reason = 'ACCOUNT_MERGE', updated_at = :now
        WHERE account_id = :sourceId
        """
    )
    abstract suspend fun moveAccountLinks(sourceId: Long, targetId: Long, now: Long)

    @Query("UPDATE bank_account_balance_observations SET account_id = :targetId WHERE account_id = :sourceId")
    abstract suspend fun moveBalanceObservations(sourceId: Long, targetId: Long)

    @Query("UPDATE bank_accounts SET merge_target_id = :targetId, is_hidden = 1, updated_at = :now WHERE id = :sourceId")
    abstract suspend fun markAccountMerged(sourceId: Long, targetId: Long, now: Long)

    @Transaction
    open suspend fun mergeAccounts(sourceId: Long, targetId: Long, now: Long) {
        moveAccountLinks(sourceId, targetId, now)
        moveBalanceObservations(sourceId, targetId)
        markAccountMerged(sourceId, targetId, now)
    }

    @Query(
        """
        SELECT run.id AS extraction_id, sms.date AS sms_date, sms.raw_address AS raw_address,
               fact.bank AS bank, fact.account AS account, fact.card_type AS card_type,
               fact.balance AS balance, fact.balance_currency AS balance_currency
        FROM ner_runs run
        INNER JOIN banking_transaction_facts fact ON fact.run_id = run.id
        INNER JOIN sms_messages sms ON sms.id = run.sms_id
        INNER JOIN sender_addresses sender ON sender.id = sms.sender_address_id
        LEFT JOIN sms_transaction_account_links link ON link.extraction_id = run.id
        WHERE run.status = 'COMPLETED' AND run.active = 1
          AND sender.is_blocked = 0 AND link.id IS NULL
        ORDER BY sms.date ASC
        LIMIT :limit
        """
    )
    abstract suspend fun accountOrganizingCandidates(limit: Int): List<AccountOrganizingCandidate>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertTransactionOverride(override: BankingTransactionOverrideEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertRun(run: NerRunEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertMentions(mentions: List<NerMentionEntity>): LongArray

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertRunMetadata(metadata: NerRunMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertTransactionFact(fact: BankingTransactionFactEntity)

    @Query(
        """
        INSERT OR IGNORE INTO ner_runs
            (sms_id, status, priority, attempts, pipeline_fingerprint, active, created_at, updated_at)
        SELECT id, 'PENDING', 'BACKFILL', 0, :pipelineFingerprint, 1, :now, :now
        FROM sms_messages
        WHERE sms_classification_type_id = 2
          AND NOT EXISTS (
              SELECT 1 FROM ner_runs existing
              WHERE existing.sms_id = sms_messages.id
                AND existing.pipeline_fingerprint = :pipelineFingerprint
          )
        """
    )
    abstract suspend fun enqueueMissingBackfill(pipelineFingerprint: String, now: Long)

    @Query(
        """
        UPDATE ner_runs
        SET status = 'PENDING', updated_at = :now, failure_code = 'PROCESS_INTERRUPTED',
            failure_message = NULL
        WHERE status = 'RUNNING' AND priority = :priority
        """
    )
    abstract suspend fun recoverInterrupted(priority: String, now: Long)

    @Query(
        """
        UPDATE ner_runs
        SET priority = 'REALTIME', status = CASE WHEN status = 'FAILED' THEN 'PENDING' ELSE status END,
            updated_at = :now
        WHERE sms_id = :smsId AND pipeline_fingerprint = :pipelineFingerprint AND status != 'COMPLETED'
        """
    )
    abstract suspend fun promoteToRealtime(smsId: Long, pipelineFingerprint: String, now: Long)

    @Transaction
    open suspend fun enqueueRealtime(smsId: Long, pipelineFingerprint: String, now: Long) {
        insertRun(
            NerRunEntity(
                smsId = smsId,
                status = "PENDING",
                priority = "REALTIME",
                pipelineFingerprint = pipelineFingerprint,
                createdAt = now,
                updatedAt = now,
            )
        )
        promoteToRealtime(smsId, pipelineFingerprint, now)
    }

    @Query(
        """
        SELECT run.id AS extraction_id, sms.id AS sms_id, sms.sender_address_id, sms.raw_address, sms.body,
               run.priority, run.attempts, sender.is_blocked, notification.notification_id
        FROM ner_runs run
        INNER JOIN sms_messages sms ON sms.id = run.sms_id
        INNER JOIN sender_addresses sender ON sender.id = sms.sender_address_id
        LEFT JOIN banking_ner_notifications notification ON notification.run_id = run.id
        WHERE run.status = 'PENDING'
        ORDER BY CASE run.priority WHEN 'REALTIME' THEN 0 ELSE 1 END,
                 run.attempts ASC,
                 CASE run.priority WHEN 'REALTIME' THEN run.created_at ELSE sms.date END ASC
        LIMIT 1
        """
    )
    abstract suspend fun nextPending(): PendingNerSms?

    @Query(
        """
        SELECT run.id AS extraction_id, sms.id AS sms_id, sms.sender_address_id, sms.raw_address, sms.body,
               run.priority, run.attempts, sender.is_blocked, notification.notification_id
        FROM ner_runs run
        INNER JOIN sms_messages sms ON sms.id = run.sms_id
        INNER JOIN sender_addresses sender ON sender.id = sms.sender_address_id
        LEFT JOIN banking_ner_notifications notification ON notification.run_id = run.id
        WHERE run.status = 'PENDING' AND run.priority = :priority
        ORDER BY run.attempts ASC, CASE run.priority WHEN 'REALTIME' THEN run.created_at ELSE sms.date END ASC
        LIMIT 1
        """
    )
    abstract suspend fun nextPendingForPriority(priority: String): PendingNerSms?

    @Query("SELECT EXISTS(SELECT 1 FROM ner_runs WHERE status = 'PENDING' AND priority = 'REALTIME')")
    abstract suspend fun hasPendingRealtime(): Boolean

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM ner_runs
            WHERE priority = 'REALTIME' AND status IN ('PENDING', 'RUNNING')
        )
        """
    )
    abstract suspend fun hasRealtimeWork(): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM ner_runs WHERE status = 'PENDING')")
    abstract suspend fun hasPending(): Boolean

    @Query(
        """
        UPDATE ner_runs
        SET status = 'RUNNING', attempts = attempts + 1, started_at = :now, updated_at = :now,
            failure_code = NULL, failure_message = NULL
        WHERE id = :runId AND status = 'PENDING'
        """
    )
    abstract suspend fun markRunning(runId: Long, now: Long): Int

    @Query(
        """
        INSERT OR REPLACE INTO banking_ner_notifications
            (id, run_id, notification_id, state, suppression_reason, posted_at, created_at, updated_at)
        VALUES (
            (SELECT id FROM banking_ner_notifications WHERE run_id = :runId),
            :runId,
            :notificationId,
            :state,
            COALESCE(:suppressionReason, (SELECT suppression_reason FROM banking_ner_notifications WHERE run_id = :runId)),
            (SELECT posted_at FROM banking_ner_notifications WHERE run_id = :runId),
            COALESCE((SELECT created_at FROM banking_ner_notifications WHERE run_id = :runId), :now),
            :now
        )
        """
    )
    abstract suspend fun updateNotificationState(
        runId: Long,
        notificationId: Int,
        state: String,
        suppressionReason: String?,
        now: Long,
    )

    @Query(
        """
        UPDATE ner_runs
        SET status = 'PENDING', updated_at = :now, failure_code = :code, failure_message = :message
        WHERE id = :runId
        """
    )
    abstract suspend fun markPendingFailure(runId: Long, now: Long, code: String, message: String?)

    @Query(
        """
        UPDATE ner_runs
        SET status = 'FAILED', updated_at = :now, completed_at = :now,
            failure_code = :code, failure_message = :message
        WHERE id = :runId
        """
    )
    abstract suspend fun markTerminalFailure(runId: Long, now: Long, code: String, message: String?)

    @Query(
        """
        UPDATE banking_ner_notifications
        SET state = 'NONE', updated_at = :now
        WHERE run_id = :runId
        """
    )
    abstract suspend fun clearNotificationState(runId: Long, now: Long)

    @Query("DELETE FROM ner_mentions WHERE run_id = :runId")
    abstract suspend fun deleteMentions(runId: Long)

    @Query(
        """
        UPDATE ner_runs
        SET status = 'COMPLETED', active = 1, stale_reason = NULL,
            completed_at = :now, updated_at = :now, failure_code = NULL, failure_message = NULL
        WHERE id = :runId
        """
    )
    abstract suspend fun markCompleted(runId: Long, now: Long)

    @Query(
        """
        UPDATE ner_runs
        SET active = 0, stale_reason = :reason, updated_at = :now
        WHERE id != :runId
          AND sms_id = (SELECT sms_id FROM ner_runs WHERE id = :runId)
          AND active = 1
        """
    )
    abstract suspend fun deactivateOtherRuns(runId: Long, reason: String, now: Long)

    @Query(
        """
        SELECT notification.run_id, notification.notification_id, notification.state,
               fact.amount, fact.account
        FROM banking_ner_notifications notification
        INNER JOIN ner_runs run ON run.id = notification.run_id
        INNER JOIN banking_transaction_facts fact ON fact.run_id = notification.run_id
        WHERE notification.run_id = :runId AND run.status = 'COMPLETED' AND run.active = 1
        LIMIT 1
        """
    )
    abstract suspend fun notificationByRunId(runId: Long): BankingNotificationRow?

    @Query(
        """
        SELECT notification.run_id
        FROM banking_ner_notifications notification
        INNER JOIN ner_runs run ON run.id = notification.run_id
        WHERE run.status = 'COMPLETED' AND run.active = 1 AND notification.state = 'READY'
        """
    )
    abstract suspend fun readyNotificationIds(): List<Long>

    @Query(
        """
        SELECT sender.is_blocked
        FROM ner_runs run
        INNER JOIN sms_messages sms ON sms.id = run.sms_id
        INNER JOIN sender_addresses sender ON sender.id = sms.sender_address_id
        WHERE run.id = :runId
        LIMIT 1
        """
    )
    abstract suspend fun isExtractionSenderBlocked(runId: Long): Boolean?

    @Query(
        """
        UPDATE banking_ner_notifications
        SET state = 'POSTED', posted_at = :now, updated_at = :now
        WHERE run_id = :runId AND state = 'READY'
        """
    )
    abstract suspend fun markNotificationPosted(runId: Long, now: Long): Int

    @Query(
        """
        UPDATE banking_ner_notifications
        SET state = 'SUPPRESSED', suppression_reason = :reason, updated_at = :now
        WHERE run_id = :runId
        """
    )
    abstract suspend fun suppressNotification(runId: Long, reason: String, now: Long)

    @Query(TRANSACTION_ROW_QUERY + " ORDER BY sms.date DESC")
    abstract fun observeTransactions(includeSmsBody: Boolean = false): Flow<List<BankingTransactionRow>>

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM sms_messages WHERE sms_classification_type_id = 2) AS eligible_count,
            (SELECT COUNT(*) FROM ner_runs WHERE status = 'PENDING') AS pending_count,
            (SELECT COUNT(*) FROM ner_runs WHERE status = 'RUNNING') AS running_count,
            (SELECT COUNT(*) FROM ner_runs WHERE status = 'COMPLETED') AS completed_count,
            (SELECT COUNT(*) FROM ner_runs WHERE status = 'FAILED') AS failed_count
        """
    )
    abstract fun observeProcessingSummary(): Flow<NerProcessingSummary>

    @Query(TRANSACTION_ROW_QUERY + " AND run.id = :extractionId LIMIT 1")
    abstract fun observeTransaction(
        extractionId: Long,
        includeSmsBody: Boolean = true,
    ): Flow<BankingTransactionRow?>

    @Query(TRANSACTION_ROW_QUERY + " AND run.id = :extractionId LIMIT 1")
    abstract suspend fun transactionById(
        extractionId: Long,
        includeSmsBody: Boolean = true,
    ): BankingTransactionRow?

    @Transaction
    open suspend fun complete(
        runId: Long,
        mentions: List<NerMentionEntity>,
        modelId: String,
        modelSha256: String,
        tokenizerSha256: String,
        preprocessingVersion: String,
        tokenCount: Int,
        truncated: Boolean,
        inferenceMs: Double,
        notificationState: String,
        now: Long,
        pipelineFingerprint: String,
        labelSchemaSha256: String? = null,
        decoderVersion: String? = null,
        normalizerVersion: String? = null,
    ) {
        mentions.forEach { NerEntityTypes.requireKnown(it.entityType) }
        deleteMentions(runId)
        val mentionIds = insertMentions(mentions)
        val persistedMentions = mentions.zip(mentionIds.asIterable()).map { (mention, id) ->
            mention.copy(id = id)
        }
        upsertRunMetadata(
            NerRunMetadataEntity(
                runId = runId,
                modelId = modelId,
                modelSha256 = modelSha256,
                tokenizerSha256 = tokenizerSha256,
                preprocessingVersion = preprocessingVersion,
                labelSchemaSha256 = labelSchemaSha256,
                decoderVersion = decoderVersion,
                normalizerVersion = normalizerVersion,
                tokenCount = tokenCount,
                truncated = truncated,
                inferenceMs = inferenceMs,
                mentionCount = mentions.size,
                createdAt = now,
                updatedAt = now,
            )
        )
        upsertTransactionFact(
            BankingTransactionFactBuilder.build(
                runId = runId,
                mentions = persistedMentions,
                truncated = truncated,
                now = now,
            )
        )
        markCompleted(runId, now)
        deactivateOtherRuns(runId, "SUPERSEDED_BY_NEWER_RUN", now)
        updateNotificationState(
            runId = runId,
            notificationId = notificationIdForRun(runId),
            state = notificationState,
            suppressionReason = null,
            now = now,
        )
    }

    private fun notificationIdForRun(runId: Long): Int =
        NOTIFICATION_ID_BASE - (runId % NOTIFICATION_ID_RANGE).toInt()

    companion object {
        private const val NOTIFICATION_ID_BASE = -2_000_000
        private const val NOTIFICATION_ID_RANGE = 1_000_000_000L

        private const val TRANSACTION_ROW_QUERY = """
            SELECT run.id AS run_id, sms.id AS sms_id,
                   sms.sender_address_id AS sender_address_id,
                   CASE WHEN :includeSmsBody = 1 THEN sms.body ELSE '' END AS sms_body,
                   sms.date AS sms_date, metadata.truncated AS truncated,
                   fact.merchant AS merchant,
                   fact.amount AS amount,
                   fact.amount_currency AS currency,
                   fact.direction AS direction,
                   fact.bank AS bank,
                   fact.account AS account,
                   fact.txn_type AS txn_type,
                   fact.card_type AS card_type,
                   fact.balance AS balance,
                   fact.balance_currency AS balance_currency,
                   fact.review_state AS review_state,
                   account_link.account_id AS account_id,
                   user_override.merchant AS override_merchant,
                   user_override.amount AS override_amount,
                   user_override.currency AS override_currency,
                   user_override.direction AS override_direction,
                   user_override.category AS override_category,
                   user_override.payment_method AS override_payment_method,
                   user_override.review_state AS override_review_state
            FROM ner_runs run
            INNER JOIN banking_transaction_facts fact ON fact.run_id = run.id
            LEFT JOIN ner_run_metadata metadata ON metadata.run_id = run.id
            INNER JOIN sms_messages sms ON sms.id = run.sms_id
            INNER JOIN sender_addresses sender ON sender.id = sms.sender_address_id
            LEFT JOIN banking_transaction_overrides user_override ON user_override.run_id = run.id
            LEFT JOIN sms_transaction_account_links account_link ON account_link.extraction_id = run.id
            WHERE run.status = 'COMPLETED' AND run.active = 1 AND sender.is_blocked = 0
        """
    }
}
