package com.summer.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.summer.core.data.local.entities.SmsNerEntity
import com.summer.core.data.local.entities.SmsNerExtractionEntity
import com.summer.core.data.local.entities.SmsTransactionOverrideEntity
import com.summer.core.data.local.entities.BankAccountEntity
import com.summer.core.data.local.entities.BankAccountBalanceObservationEntity
import com.summer.core.data.local.entities.SmsTransactionAccountLinkEntity
import com.summer.core.data.local.model.AccountOrganizingCandidate
import com.summer.core.data.local.model.PendingNerSms
import com.summer.core.data.local.model.CompletedNerExtraction
import com.summer.core.data.local.model.NerProcessingSummary
import com.summer.core.data.local.model.TransactionProjection
import com.summer.core.ner.NerConstants
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
        SELECT extraction.id AS extraction_id, sms.date AS sms_date, sms.raw_address AS raw_address,
               (SELECT raw_text FROM sms_ner_entities WHERE extraction_id = extraction.id AND entity_type = 'BANK' ORDER BY entity_order LIMIT 1) AS bank,
               (SELECT normalized_value FROM sms_ner_entities WHERE extraction_id = extraction.id AND entity_type = 'ACCOUNT' ORDER BY entity_order LIMIT 1) AS account,
               (SELECT normalized_value FROM sms_ner_entities WHERE extraction_id = extraction.id AND entity_type = 'CARD_TYPE' ORDER BY entity_order LIMIT 1) AS card_type,
               (SELECT normalized_value FROM sms_ner_entities WHERE extraction_id = extraction.id AND entity_type = 'BALANCE' ORDER BY entity_order LIMIT 1) AS balance,
               (SELECT raw_text FROM sms_ner_entities WHERE extraction_id = extraction.id AND entity_type = 'BALANCE' ORDER BY entity_order LIMIT 1) AS raw_balance
        FROM sms_ner_extractions extraction
        INNER JOIN sms_messages sms ON sms.id = extraction.sms_id
        INNER JOIN sender_addresses sender ON sender.id = sms.sender_address_id
        LEFT JOIN sms_transaction_account_links link ON link.extraction_id = extraction.id
        WHERE extraction.status = 'COMPLETED' AND sender.is_blocked = 0 AND link.id IS NULL
        ORDER BY sms.date ASC
        LIMIT :limit
        """
    )
    abstract suspend fun accountOrganizingCandidates(limit: Int): List<AccountOrganizingCandidate>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertTransactionOverride(override: SmsTransactionOverrideEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertExtraction(extraction: SmsNerExtractionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertEntities(entities: List<SmsNerEntity>)

    @Query(
        """
        INSERT OR IGNORE INTO sms_ner_extractions
            (sms_id, status, priority, attempts, notification_ready, notification_state, created_at, updated_at)
        SELECT id, 'PENDING', 'BACKFILL', 0, 0, 'NONE', :now, :now
        FROM sms_messages
        WHERE sms_classification_type_id = 2
        """
    )
    abstract suspend fun enqueueMissingBackfill(now: Long)

    @Query(
        """
        UPDATE sms_ner_extractions
        SET status = 'PENDING', updated_at = :now, failure_code = 'PROCESS_INTERRUPTED',
            failure_message = NULL
        WHERE status = 'RUNNING' AND priority = :priority
        """
    )
    abstract suspend fun recoverInterrupted(priority: String, now: Long)

    @Query(
        """
        UPDATE sms_ner_extractions
        SET priority = 'REALTIME', status = CASE WHEN status = 'FAILED' THEN 'PENDING' ELSE status END,
            updated_at = :now
        WHERE sms_id = :smsId AND status != 'COMPLETED'
        """
    )
    abstract suspend fun promoteToRealtime(smsId: Long, now: Long)

    @Transaction
    open suspend fun enqueueRealtime(smsId: Long, now: Long) {
        insertExtraction(
            SmsNerExtractionEntity(
                smsId = smsId,
                status = "PENDING",
                priority = "REALTIME",
                createdAt = now,
                updatedAt = now,
            )
        )
        promoteToRealtime(smsId, now)
    }

    @Query(
        """
        SELECT e.id AS extraction_id, s.id AS sms_id, s.sender_address_id, s.raw_address, s.body,
               e.priority, e.attempts, sender.is_blocked, e.notification_id
        FROM sms_ner_extractions e
        INNER JOIN sms_messages s ON s.id = e.sms_id
        INNER JOIN sender_addresses sender ON sender.id = s.sender_address_id
        WHERE e.status = 'PENDING'
        ORDER BY CASE e.priority WHEN 'REALTIME' THEN 0 ELSE 1 END,
                 e.attempts ASC,
                 CASE e.priority WHEN 'REALTIME' THEN e.created_at ELSE s.date END ASC
        LIMIT 1
        """
    )
    abstract suspend fun nextPending(): PendingNerSms?

    @Query(
        """
        SELECT e.id AS extraction_id, s.id AS sms_id, s.sender_address_id, s.raw_address, s.body,
               e.priority, e.attempts, sender.is_blocked, e.notification_id
        FROM sms_ner_extractions e
        INNER JOIN sms_messages s ON s.id = e.sms_id
        INNER JOIN sender_addresses sender ON sender.id = s.sender_address_id
        WHERE e.status = 'PENDING' AND e.priority = :priority
        ORDER BY e.attempts ASC, CASE e.priority WHEN 'REALTIME' THEN e.created_at ELSE s.date END ASC
        LIMIT 1
        """
    )
    abstract suspend fun nextPendingForPriority(priority: String): PendingNerSms?

    @Query("SELECT EXISTS(SELECT 1 FROM sms_ner_extractions WHERE status = 'PENDING' AND priority = 'REALTIME')")
    abstract suspend fun hasPendingRealtime(): Boolean

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM sms_ner_extractions
            WHERE priority = 'REALTIME' AND status IN ('PENDING', 'RUNNING')
        )
        """
    )
    abstract suspend fun hasRealtimeWork(): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM sms_ner_extractions WHERE status = 'PENDING')")
    abstract suspend fun hasPending(): Boolean

    @Query(
        """
        UPDATE sms_ner_extractions
        SET status = 'RUNNING', attempts = attempts + 1, started_at = :now, updated_at = :now,
            failure_code = NULL, failure_message = NULL
        WHERE id = :extractionId AND status = 'PENDING'
        """
    )
    abstract suspend fun markRunning(extractionId: Long, now: Long): Int

    @Query(
        """
        UPDATE sms_ner_extractions
        SET notification_id = :notificationId, notification_state = :state,
            notification_suppression_reason = :suppressionReason, updated_at = :now
        WHERE id = :extractionId
        """
    )
    abstract suspend fun updateNotificationState(
        extractionId: Long,
        notificationId: Int,
        state: String,
        suppressionReason: String?,
        now: Long,
    )

    @Query(
        """
        UPDATE sms_ner_extractions
        SET status = 'PENDING', updated_at = :now, failure_code = :code, failure_message = :message
        WHERE id = :extractionId
        """
    )
    abstract suspend fun markPendingFailure(extractionId: Long, now: Long, code: String, message: String?)

    @Query(
        """
        UPDATE sms_ner_extractions
        SET status = 'FAILED', updated_at = :now, completed_at = :now,
            failure_code = :code, failure_message = :message
        WHERE id = :extractionId
        """
    )
    abstract suspend fun markTerminalFailure(extractionId: Long, now: Long, code: String, message: String?)

    @Query(
        """
        UPDATE sms_ner_extractions
        SET notification_state = 'NONE', notification_ready = 0, updated_at = :now
        WHERE id = :extractionId
        """
    )
    abstract suspend fun clearNotificationState(extractionId: Long, now: Long)

    @Query("DELETE FROM sms_ner_entities WHERE extraction_id = :extractionId")
    abstract suspend fun deleteEntities(extractionId: Long)

    @Query(
        """
        UPDATE sms_ner_extractions
        SET status = 'COMPLETED', model_id = :modelId, model_sha256 = :modelSha256,
            tokenizer_sha256 = :tokenizerSha256, preprocessing_version = :preprocessingVersion,
            token_count = :tokenCount, truncated = :truncated, inference_ms = :inferenceMs,
            entity_count = :entityCount, notification_ready = :notificationReady,
            notification_state = :notificationState, completed_at = :now,
            updated_at = :now, failure_code = NULL, failure_message = NULL
        WHERE id = :extractionId
        """
    )
    abstract suspend fun markCompleted(
        extractionId: Long,
        modelId: String,
        modelSha256: String,
        tokenizerSha256: String,
        preprocessingVersion: String,
        tokenCount: Int,
        truncated: Boolean,
        inferenceMs: Double,
        entityCount: Int,
        notificationReady: Boolean,
        notificationState: String,
        now: Long,
    )

    @Transaction
    @Query("SELECT * FROM sms_ner_extractions WHERE status = 'COMPLETED' ORDER BY completed_at DESC")
    abstract fun observeCompleted(): Flow<List<CompletedNerExtraction>>

    @Transaction
    @Query("SELECT * FROM sms_ner_extractions WHERE id = :extractionId AND status = 'COMPLETED' LIMIT 1")
    abstract suspend fun completedById(extractionId: Long): CompletedNerExtraction?

    @Query("SELECT id FROM sms_ner_extractions WHERE status = 'COMPLETED' AND notification_state = 'READY'")
    abstract suspend fun readyNotificationIds(): List<Long>

    @Query(
        """
        SELECT sender.is_blocked
        FROM sms_ner_extractions extraction
        INNER JOIN sms_messages sms ON sms.id = extraction.sms_id
        INNER JOIN sender_addresses sender ON sender.id = sms.sender_address_id
        WHERE extraction.id = :extractionId
        LIMIT 1
        """
    )
    abstract suspend fun isExtractionSenderBlocked(extractionId: Long): Boolean?

    @Query(
        """
        UPDATE sms_ner_extractions
        SET notification_state = 'POSTED', notification_ready = 0,
            result_notification_posted_at = :now, updated_at = :now
        WHERE id = :extractionId AND notification_state = 'READY'
        """
    )
    abstract suspend fun markNotificationPosted(extractionId: Long, now: Long): Int

    @Query(
        """
        UPDATE sms_ner_extractions
        SET notification_state = 'SUPPRESSED', notification_ready = 0,
            notification_suppression_reason = :reason, updated_at = :now
        WHERE id = :extractionId
        """
    )
    abstract suspend fun suppressNotification(extractionId: Long, reason: String, now: Long)

    @Query(TRANSACTION_PROJECTION_QUERY + " ORDER BY sms.date DESC")
    abstract fun observeTransactions(includeSmsBody: Boolean = false): Flow<List<TransactionProjection>>

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM sms_messages WHERE sms_classification_type_id = 2) AS eligible_count,
            (SELECT COUNT(*) FROM sms_ner_extractions WHERE status = 'PENDING') AS pending_count,
            (SELECT COUNT(*) FROM sms_ner_extractions WHERE status = 'RUNNING') AS running_count,
            (SELECT COUNT(*) FROM sms_ner_extractions WHERE status = 'COMPLETED') AS completed_count,
            (SELECT COUNT(*) FROM sms_ner_extractions WHERE status = 'FAILED') AS failed_count
        """
    )
    abstract fun observeProcessingSummary(): Flow<NerProcessingSummary>

    @Query(TRANSACTION_PROJECTION_QUERY + " AND extraction.id = :extractionId LIMIT 1")
    abstract fun observeTransaction(
        extractionId: Long,
        includeSmsBody: Boolean = true,
    ): Flow<TransactionProjection?>

    @Query(TRANSACTION_PROJECTION_QUERY + " AND extraction.id = :extractionId LIMIT 1")
    abstract suspend fun transactionById(
        extractionId: Long,
        includeSmsBody: Boolean = true,
    ): TransactionProjection?

    @Transaction
    open suspend fun complete(
        extractionId: Long,
        entities: List<SmsNerEntity>,
        modelId: String,
        modelSha256: String,
        tokenizerSha256: String,
        preprocessingVersion: String,
        tokenCount: Int,
        truncated: Boolean,
        inferenceMs: Double,
        notificationState: String,
        now: Long,
    ) {
        deleteEntities(extractionId)
        insertEntities(entities)
        markCompleted(
            extractionId, modelId, modelSha256, tokenizerSha256, preprocessingVersion,
            tokenCount, truncated, inferenceMs, entities.size,
            notificationState == NerConstants.NOTIFICATION_STATE_READY, notificationState,
            now,
        )
    }

    companion object {
        private const val TRANSACTION_PROJECTION_QUERY = """
            SELECT extraction.id AS extraction_id, sms.id AS sms_id,
                   sms.sender_address_id AS sender_address_id,
                   CASE WHEN :includeSmsBody = 1 THEN sms.body ELSE '' END AS sms_body,
                   sms.date AS sms_date, extraction.truncated AS truncated,
                   (SELECT raw_text FROM sms_ner_entities WHERE extraction_id = extraction.id AND entity_type = 'MERCHANT' ORDER BY entity_order LIMIT 1) AS raw_merchant,
                   (SELECT raw_text FROM sms_ner_entities WHERE extraction_id = extraction.id AND entity_type = 'AMOUNT' ORDER BY entity_order LIMIT 1) AS raw_amount,
                   (SELECT normalized_value FROM sms_ner_entities WHERE extraction_id = extraction.id AND entity_type = 'AMOUNT' ORDER BY entity_order LIMIT 1) AS normalized_amount,
                   (SELECT raw_text FROM sms_ner_entities WHERE extraction_id = extraction.id AND entity_type = 'DIRECTION' ORDER BY entity_order LIMIT 1) AS raw_direction,
                   (SELECT normalized_value FROM sms_ner_entities WHERE extraction_id = extraction.id AND entity_type = 'DIRECTION' ORDER BY entity_order LIMIT 1) AS normalized_direction,
                   (SELECT raw_text FROM sms_ner_entities WHERE extraction_id = extraction.id AND entity_type = 'BANK' ORDER BY entity_order LIMIT 1) AS bank,
                   (SELECT normalized_value FROM sms_ner_entities WHERE extraction_id = extraction.id AND entity_type = 'ACCOUNT' ORDER BY entity_order LIMIT 1) AS account,
                   (SELECT normalized_value FROM sms_ner_entities WHERE extraction_id = extraction.id AND entity_type = 'TXN_TYPE' ORDER BY entity_order LIMIT 1) AS txn_type,
                   (SELECT normalized_value FROM sms_ner_entities WHERE extraction_id = extraction.id AND entity_type = 'CARD_TYPE' ORDER BY entity_order LIMIT 1) AS card_type,
                   (SELECT normalized_value FROM sms_ner_entities WHERE extraction_id = extraction.id AND entity_type = 'BALANCE' ORDER BY entity_order LIMIT 1) AS balance,
                   (SELECT raw_text FROM sms_ner_entities WHERE extraction_id = extraction.id AND entity_type = 'BALANCE' ORDER BY entity_order LIMIT 1) AS raw_balance,
                   account_link.account_id AS account_id,
                   user_override.merchant AS override_merchant,
                   user_override.amount AS override_amount,
                   user_override.currency AS override_currency,
                   user_override.direction AS override_direction,
                   user_override.category AS override_category,
                   user_override.payment_method AS override_payment_method,
                   user_override.review_state AS override_review_state
            FROM sms_ner_extractions extraction
            INNER JOIN sms_messages sms ON sms.id = extraction.sms_id
            INNER JOIN sender_addresses sender ON sender.id = sms.sender_address_id
            LEFT JOIN sms_transaction_overrides user_override ON user_override.extraction_id = extraction.id
            LEFT JOIN sms_transaction_account_links account_link ON account_link.extraction_id = extraction.id
            WHERE extraction.status = 'COMPLETED' AND sender.is_blocked = 0
        """
    }
}
