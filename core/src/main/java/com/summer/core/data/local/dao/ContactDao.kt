package com.summer.core.data.local.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.summer.core.android.phone.data.entity.ContactEntity
import com.summer.core.data.local.model.ContactInfoInboxModel
import com.summer.core.data.local.model.ContactMessageInfoModel
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateContact(contact: ContactEntity)

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun getContactById(id: Long): ContactEntity?

    @Query("SELECT * FROM contacts")
    suspend fun getAllContacts(): List<ContactEntity>

    @Query("DELETE FROM contacts")
    suspend fun clearContacts()

    @Query(
        """
    SELECT 
    COALESCE(c1.name, c2.name, sa.original_sender_address) AS sender_name,
    s.raw_address,
    s.body AS last_message,
    s.date AS last_message_date,
    sa.id AS sender_address_id,
    sa.sender_type AS sender_type,
    (
        SELECT COUNT(*) 
        FROM sms_messages sm
        INNER JOIN sms_classification_types sct ON sm.sms_classification_type_id = sct.id
        WHERE sm.sender_address_id = sa.id
          AND sm.read = 0
          AND sct.is_important = :isImportant
    ) AS unread_count
    FROM sender_addresses sa
    INNER JOIN sms_messages s ON s.id = (
    SELECT sm.id
    FROM sms_messages sm
    INNER JOIN sms_classification_types sct ON sm.sms_classification_type_id = sct.id
    WHERE sm.sender_address_id = sa.id
      AND sa.is_blocked = 0
      AND sct.is_important = :isImportant
    ORDER BY sm.date DESC
    LIMIT 1
)
LEFT JOIN contacts c1 ON c1.phone_number = sa.sender_address
LEFT JOIN contacts c2 ON c2.original_phone_number = sa.original_sender_address
ORDER BY s.date DESC
"""
    )
    fun getContactListByImportancePaged(isImportant: Boolean): PagingSource<Int, ContactMessageInfoModel>

    @Query(
        """
    SELECT 
    COALESCE(c1.name, c2.name, sa.original_sender_address) AS sender_name,
    s.raw_address AS raw_address,
    s.body AS last_message,
    s.date AS last_message_date,
    sa.id AS sender_address_id,
    sa.sender_type AS sender_type,
    (
        SELECT COUNT(*)
        FROM sms_messages sm
        WHERE sm.sender_address_id = sa.id
          AND sm.read = 0
    ) AS unread_count
FROM sender_addresses sa
INNER JOIN sms_messages s ON s.id = (
    SELECT sm.id
    FROM sms_messages sm
    WHERE sm.sender_address_id = sa.id AND sa.is_blocked = 0
    ORDER BY sm.date DESC
    LIMIT 1
)
LEFT JOIN contacts c1 ON c1.phone_number = sa.sender_address
LEFT JOIN contacts c2 ON c2.original_phone_number = sa.original_sender_address
WHERE lower(COALESCE(c1.name, c2.name, sa.original_sender_address)) LIKE '%' || :query || '%'
    AND sa.is_blocked = 0
ORDER BY s.date DESC
LIMIT :limit
    """
    )
    suspend fun searchConversations(query: String, limit: Int): List<ContactMessageInfoModel>

    @Query(
        """
    SELECT 
        COALESCE(c1.name, c2.name, sa.original_sender_address) AS sender_name,
        s.raw_address AS raw_address,
        s.body AS last_message,
        s.date AS last_message_date,
        sa.id AS sender_address_id,
        sa.sender_type AS sender_type,
        (
            SELECT COUNT(*)
            FROM sms_messages sm
            WHERE sm.sender_address_id = sa.id
              AND sm.read = 0
        ) AS unread_count
    FROM sender_addresses sa
    INNER JOIN sms_messages s ON s.id = (
        SELECT sm.id
        FROM sms_messages sm
        WHERE sm.sender_address_id = sa.id
        ORDER BY sm.date DESC
        LIMIT 1
    )
    LEFT JOIN contacts c1 ON c1.phone_number = sa.sender_address
    LEFT JOIN contacts c2 ON c2.original_phone_number = sa.original_sender_address
    WHERE lower(COALESCE(c1.name, c2.name, sa.original_sender_address)) LIKE '%' || :query || '%'
        AND sa.is_blocked = 0
    ORDER BY s.date DESC
    """
    )
    fun getSearchConversationsPagingSource(query: String): PagingSource<Int, ContactMessageInfoModel>

    @Query(
        """
    SELECT COUNT(*) FROM sender_addresses sa
    LEFT JOIN contacts c1 ON c1.phone_number = sa.sender_address
    LEFT JOIN contacts c2 ON c2.original_phone_number = sa.original_sender_address
    WHERE lower(COALESCE(c1.name, c2.name, sa.original_sender_address)) LIKE '%' || :query || '%'
        AND sa.is_blocked = 0
    """
    )
    suspend fun getConversationsMatchCount(query: String): Int

    @Query(
        """
    SELECT 
        COALESCE(c1.name, c2.name, sa.original_sender_address) AS sender_name,
        sa.id AS sender_address_id,
        sa.sender_type AS sender_type,
        CASE 
            WHEN sa.sender_type = 'CONTACT' THEN COALESCE(c1.phone_number, c2.phone_number)
            ELSE NULL
        END AS phone_number
    FROM sender_addresses sa
    LEFT JOIN contacts c1 ON c1.phone_number = sa.sender_address
    LEFT JOIN contacts c2 ON c2.original_phone_number = sa.original_sender_address
    WHERE sa.id = :senderAddressId
      AND sa.is_blocked = 0
    """
    )
    fun getContactInfoBySenderAddressId(
        senderAddressId: Long
    ): Flow<ContactInfoInboxModel?>

    @Query(
        """
    SELECT COALESCE(c1.name, c2.name, sa.original_sender_address) AS sender_name
    FROM sender_addresses sa
    LEFT JOIN contacts c1 ON c1.phone_number = sa.sender_address
    LEFT JOIN contacts c2 ON c2.original_phone_number = sa.original_sender_address
    WHERE sa.id = :senderAddressId
      AND sa.is_blocked = 0
    LIMIT 1
"""
    )
    suspend fun getSenderNameById(senderAddressId: Long): String

    @Query(
        """
        SELECT * FROM contacts
        WHERE name LIKE '%' || :query || '%'
        OR phone_number LIKE '%' || :query || '%'
        ORDER BY lower(name)
    """
    )
    fun getContactsWithFilter(query: String): PagingSource<Int, ContactEntity>

    @Query(
        """
    SELECT * FROM contacts
    WHERE lower(name) LIKE '%' || :query || '%'
       OR lower(phone_number) LIKE '%' || :query || '%'
    ORDER BY lower(name)
    LIMIT :limit
    """
    )
    suspend fun searchContacts(
        query: String,
        limit: Int
    ): List<ContactEntity>

    @Query(
        """
    SELECT * FROM contacts
    WHERE lower(name) LIKE '%' || :query || '%'
       OR lower(phone_number) LIKE '%' || :query || '%'
    ORDER BY lower(name)
    """
    )
    fun getSearchContactsPagingSource(query: String): PagingSource<Int, ContactEntity>

    @Query(
        """
    SELECT COUNT(*) FROM contacts
    WHERE name LIKE '%' || :query || '%'
       OR phone_number LIKE '%' || :query || '%'
    """
    )
    suspend fun getContactsCount(query: String): Int

    @Query("UPDATE sender_addresses SET is_blocked = 1 WHERE id = :senderAddressId")
    suspend fun blockSender(senderAddressId: Long)

    @Query("UPDATE sender_addresses SET is_blocked = 0 WHERE id = :senderAddressId")
    suspend fun unblockSender(senderAddressId: Long)

    @Query(
        """
    SELECT 
        COALESCE(c1.name, c2.name, sa.original_sender_address) AS sender_name,
        sa.id AS sender_address_id,
        CASE 
            WHEN sa.sender_type = 'CONTACT' THEN COALESCE(c1.phone_number, c2.phone_number)
            ELSE NULL
        END AS phone_number,
        sa.sender_type AS sender_type
    FROM sender_addresses sa
    LEFT JOIN contacts c1 ON c1.phone_number = sa.sender_address
    LEFT JOIN contacts c2 ON c2.original_phone_number = sa.original_sender_address
    WHERE sa.is_blocked = 1
      AND lower(COALESCE(c1.name, c2.name, sa.original_sender_address)) LIKE '%' || :query || '%'
    ORDER BY sender_name COLLATE NOCASE
    """
    )
    fun getSearchBlockedSendersPagingSource(
        query: String
    ): PagingSource<Int, ContactInfoInboxModel>
}