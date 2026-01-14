package com.summer.core.data.local.entities

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import com.summer.core.data.local.entities.SmsEntity.Companion.TABLE_NAME

@Keep
@Entity(
    tableName = TABLE_NAME,
    foreignKeys = [
        ForeignKey(
            entity = SenderAddressEntity::class,
            parentColumns = ["id"],
            childColumns = ["sender_address_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(
        value = ["sender_address_id"],
        unique = false
    ), Index(value = ["android_sms_id"], unique = true)]
)
data class SmsEntity(
    @ColumnInfo(name = "id")
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "android_sms_id")  // Unique SMS ID from the system
    val androidSmsId: Int?,

    @ColumnInfo(name = "sender_address_id")  // sender's id
    val senderAddressId: Long,

    @ColumnInfo(name = "raw_address")  // Sender/Receiver Number
    val rawAddress: String,

    @ColumnInfo(name = "body")  // SMS content
    val body: String,

    @ColumnInfo(name = "date")  // Received/Sent Timestamp
    val date: Long,

    @ColumnInfo(name = "date_sent")  // When the sender originally sent it
    val dateSent: Long?,

    @ColumnInfo(name = "type")  // Sent (2) or Received (1)
    val type: Int,

    @ColumnInfo(name = "thread_id")  // Thread/conversation ID
    val threadId: Long?,

    @ColumnInfo(name = "read")  // 0 = Unread, 1 = Read
    val read: Int,

    @ColumnInfo(name = "status")  // -1 = None, 0 = Complete, 32 = Pending, 64 = Failed
    val status: Int?,

    @ColumnInfo(name = "service_center")  // SMS Service Center Address
    val serviceCenter: String?,

    @ColumnInfo(name = "subscription_id")  // SIM ID (Useful for dual SIM)
    val subscriptionId: Int?,

    @ColumnInfo(name = "sms_classification_type_id")  // Bert classification mapped to classification_type table
    val smsClassificationTypeId: Int?,

    @ColumnInfo(name = "importance_score")  // Bert message importance score
    val importanceScore: Int?,

    @ColumnInfo(name = "confidence_score")  // Bert confidence score
    val confidenceScore: Float?,

    @ColumnInfo(name = "created_at_app")  // Row created-at, in epoch
    val createdAtApp: Long,

    @ColumnInfo(name = "updated_at_app")  // Row updated-at, in epoch
    val updatedAtApp: Long,
) {

    @Ignore
    var senderName: String? = null

    companion object {
        const val TABLE_NAME = "sms_messages"
    }
}