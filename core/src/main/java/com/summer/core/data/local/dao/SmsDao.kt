package com.summer.core.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.summer.core.data.local.entities.SenderAddressEntity
import com.summer.core.data.local.entities.SenderType
import com.summer.core.data.local.entities.SmsClassificationTypeEntity
import com.summer.core.data.local.entities.SmsEntity
import com.summer.core.data.local.model.SearchSmsMessageQueryModel
import com.summer.core.data.local.model.SmsMessageModel
import com.summer.core.data.local.model.SmsPagingKey
import com.summer.core.util.determineSenderType
import com.summer.core.util.normalizePhoneNumber
import com.summer.core.util.trimSenderId
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllSmsMessages(smsList: List<SmsEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSmsMessage(sms: SmsEntity): Long

    @Query("SELECT * FROM sms_messages ORDER BY android_sms_id DESC LIMIT 1")
    suspend fun getLastInsertedSmsMessageByAndroidSmsId(): SmsEntity?

    @Query("SELECT COUNT(*) FROM sms_messages")
    suspend fun getTotalProcessedSmsCount(): Int

    @Query("SELECT id FROM sender_addresses WHERE sender_address = :address LIMIT 1")
    fun getSenderIdByAddress(address: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertSenderAddress(sender: SenderAddressEntity): Long

    @Transaction
    suspend fun getOrInsertSenderId(senderAddress: String, defaultCountryCode: Int): Long {
        val senderType = senderAddress.determineSenderType()

        val normalizedAddress = when (senderType) {
            SenderType.BUSINESS -> senderAddress.trimSenderId()
            SenderType.CONTACT -> senderAddress.normalizePhoneNumber(defaultCountryCode)
        }.uppercase()

        // Check if the sender already exists
        val existingId = getSenderIdByAddress(normalizedAddress)
        if (existingId != null) return existingId

        // Insert new sender and return its ID
        return insertSenderAddress(
            SenderAddressEntity(
                senderAddress = normalizedAddress,
                originalSenderAddress = senderAddress, // Store original for display
                senderType = senderType
            )
        )
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSmsClassificationTypes(classificationTypes: List<SmsClassificationTypeEntity>)

    @Query(
        """
    SELECT 
        sms.id AS id,
        sms.body AS body,
        sms.date AS date,
        sms.type AS type,
        classification.sms_type AS sms_message_type,
        classification.compact_sms_type AS compact_type,
        sms.importance_score AS importance_score,
        sms.confidence_score AS confidence_score
    FROM sms_messages AS sms
    LEFT JOIN sms_classification_types AS classification
        ON sms.sms_classification_type_id = classification.id
    WHERE sms.sender_address_id = :senderAddressId
    AND (:important = -1  OR classification.is_important = :important)
    ORDER BY sms.date DESC
"""
    )
    fun getPagedSmsMessagesPagedBySenderAddressId(
        senderAddressId: Long,
        important: Int
    ): PagingSource<Int, SmsMessageModel>

    @Query(
        """
    SELECT 
        sms.id AS id,
        sms.sender_address_id AS sender_address_id,
        COALESCE(c.name, sender.sender_address) AS sender_address,
        sms.body AS body,
        sms.date AS date,
        sms.type AS type,
        classification.sms_type AS sms_message_type,
        classification.compact_sms_type AS compact_type
    FROM sms_messages AS sms
    LEFT JOIN sms_classification_types AS classification
        ON sms.sms_classification_type_id = classification.id
    INNER JOIN sender_addresses AS sender
        ON sms.sender_address_id = sender.id
    LEFT JOIN contacts c
        ON sender.sender_type = 'CONTACT'
        AND c.phone_number = sender.sender_address
    WHERE lower(sms.body) LIKE '%' || :query || '%'
      AND sender.is_blocked = 0
    ORDER BY sms.date DESC
    LIMIT :limit
    """
    )
    suspend fun searchMessages(
        query: String,
        limit: Int
    ): List<SearchSmsMessageQueryModel>

    @Query(
        """
    SELECT 
        sms.id AS id,
        sms.sender_address_id AS sender_address_id,
        COALESCE(c.name, sender.sender_address) AS sender_address,
        sms.body AS body,
        sms.date AS date,
        sms.type AS type,
        classification.sms_type AS sms_message_type,
        classification.compact_sms_type AS compact_type
    FROM sms_messages AS sms
    LEFT JOIN sms_classification_types AS classification
        ON sms.sms_classification_type_id = classification.id
    INNER JOIN sender_addresses AS sender
        ON sms.sender_address_id = sender.id
    LEFT JOIN contacts c
        ON sender.sender_type = 'CONTACT'
        AND c.phone_number = sender.sender_address
    WHERE lower(sms.body) LIKE '%' || :query || '%'
      AND (:senderAddressId = 0 OR sms.sender_address_id = :senderAddressId)
      AND sender.is_blocked = 0
    ORDER BY sms.date DESC
    """
    )
    fun getSearchMessagesPagingSource(
        query: String,
        senderAddressId: Long
    ): PagingSource<Int, SearchSmsMessageQueryModel>

    @Query(
        """
    SELECT COUNT(*) FROM sms_messages AS sms
    INNER JOIN sender_addresses AS sender ON sms.sender_address_id = sender.id
    WHERE lower(sms.body) LIKE '%' || :query || '%'
    AND sender.is_blocked = 0
    """
    )
    suspend fun getMessagesMatchCount(query: String): Int

    @Query("SELECT android_sms_id FROM sms_messages WHERE sender_address_id = :senderAddressId and read = 0")
    suspend fun getUnreadAndroidSmsIdsBySenderAddressId(senderAddressId: Long): List<Long>

    @Query("UPDATE sms_messages set read = 1 where sender_address_id = :senderAddressId")
    suspend fun markSmsAsReadBySenderAddressId(senderAddressId: Long)

    @Query("SELECT android_sms_id FROM sms_messages WHERE id = :id")
    suspend fun getAndroidSmsIdById(id: Long): Int?

    @Query("UPDATE sms_messages SET status = :status, updated_at_app = :updatedAt WHERE id = :id")
    suspend fun updateSmsStatusById(id: Long, status: Int, updatedAt: Long)

    @Query("SELECT * FROM sms_messages WHERE id = :id")
    suspend fun getSmsEntityById(id: Long): SmsEntity?

    @Query("UPDATE sms_messages SET android_sms_id = :androidSmsId WHERE id = :id")
    suspend fun updateAndroidSmsId(id: Long, androidSmsId: Int)

    @Query("SELECT * FROM sms_messages WHERE android_sms_id IS NOT NULL ORDER BY android_sms_id ASC LIMIT 1")
    suspend fun getFirstInsertedSmsMessageByAndroidSmsId(): SmsEntity?

    @Query(
        """
        SELECT id, date FROM sms_messages
        WHERE id = :smsId
        LIMIT 1
    """
    )
    suspend fun getDateAndId(smsId: Long): SmsPagingKey?

    @Query(
        """
        SELECT 
            sms.id AS id,
            sms.android_sms_id AS android_sms_id,
            sms.body AS body,
            sms.date AS date,
            sms.type AS type,
            classification.sms_type AS sms_message_type,
            classification.compact_sms_type AS compact_type,
            sms.importance_score AS importance_score,
            sms.confidence_score AS confidence_score
        FROM sms_messages AS sms
        LEFT JOIN sms_classification_types AS classification
            ON sms.sms_classification_type_id = classification.id
        WHERE sms.sender_address_id = :senderAddressId
        AND (:important = -1  OR classification.is_important = :important)
          AND (sms.date < :date OR (sms.date = :date AND sms.id <= :id))
        ORDER BY sms.date DESC, sms.id DESC
        LIMIT :limit
    """
    )
    suspend fun getMessagesBefore(
        senderAddressId: Long,
        date: Long,
        important: Int,
        id: Long,
        limit: Int
    ): List<SmsMessageModel>

    @Query(
        """
        SELECT 
            sms.id AS id,
            sms.android_sms_id AS android_sms_id,
            sms.body AS body,
            sms.date AS date,
            sms.type AS type,
            classification.sms_type AS sms_message_type,
            classification.compact_sms_type AS compact_type,
            sms.importance_score AS importance_score,
            sms.confidence_score AS confidence_score
        FROM sms_messages AS sms
        LEFT JOIN sms_classification_types AS classification
            ON sms.sms_classification_type_id = classification.id
        WHERE sms.sender_address_id = :senderAddressId
            AND (:important = -1  OR classification.is_important = :important)
          AND (sms.date > :date OR (sms.date = :date AND sms.id > :id))
        ORDER BY sms.date ASC, sms.id ASC
        LIMIT :limit
    """
    )
    suspend fun getMessagesAfter(
        senderAddressId: Long,
        date: Long,
        important: Int,
        id: Long,
        limit: Int
    ): List<SmsMessageModel>

    @Query("SELECT MAX(date) FROM sms_messages WHERE sender_address_id = :senderId")
    fun observeMaxDateBySenderId(senderId: Long): Flow<Long?>

    @Query("SELECT MIN(date) FROM sms_messages WHERE sender_address_id = :senderId")
    fun observeMinDateBySenderId(senderId: Long): Flow<Long?>

    @Query("DELETE FROM sms_messages WHERE id IN (:ids)")
    suspend fun deleteSmsMessagesByIds(ids: List<Long>)

    @Query("SELECT * FROM sms_classification_types ORDER BY id ASC")
    suspend fun getAllSmsClassificationTypes(): List<SmsClassificationTypeEntity>

    @Query("UPDATE sms_classification_types SET is_important = :isImportant WHERE id = :id")
    suspend fun updateSmsTypeImportance(id: Int, isImportant: Boolean)

    @Query("SELECT is_blocked FROM sender_addresses WHERE id = :senderAddressId LIMIT 1")
    suspend fun isSenderBlocked(senderAddressId: Long): Boolean
}