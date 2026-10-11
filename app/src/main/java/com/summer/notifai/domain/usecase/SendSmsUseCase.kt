package com.summer.notifai.domain.usecase

import android.content.Context
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.summer.core.android.sms.processor.SmsSender
import com.summer.core.android.sms.util.SmsMessageType
import com.summer.core.android.sms.util.SmsStatus
import com.summer.core.android.sms.util.SmsUtils
import com.summer.core.data.local.entities.SmsEntity
import com.summer.notifai.domain.repository.ISmsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SendSmsUseCase @Inject constructor(
    private val smsRepository: ISmsRepository
) {
    /**
     * Sends an SMS by first inserting a PENDING message in local DB and then triggering SmsManager.
     * @param context Application context
     * @param senderAddressId ID linking to the sender (used for Room thread and contact matching)
     * @param phoneNumber Destination number (preferably E.164 format)
     * @param body SMS content
     * @return ID of the inserted message or null on failure
     */
    suspend operator fun invoke(
        context: Context,
        senderAddressId: Long,
        phoneNumber: String,
        body: String
    ): Long? = withContext(Dispatchers.IO) {
        try {
            val smsEntity = getSmsEntity(context, senderAddressId, phoneNumber, body)
            val insertedId = smsRepository.insertSms(smsEntity = smsEntity)

            SmsSender.sendSms(
                context = context,
                id = insertedId,
                address = phoneNumber,
                body = body
            )

            insertedId
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
            Log.e("SendSmsUseCase", "Failed to send SMS", e)
            null
        }
    }

    private suspend fun getSmsEntity(
        context: Context,
        senderAddressId: Long,
        phoneNumber: String,
        body: String
    ): SmsEntity {
        val timestamp = System.currentTimeMillis()
        return SmsEntity(
            androidSmsId = null,
            senderAddressId = senderAddressId,
            rawAddress = phoneNumber,
            body = body,
            date = timestamp,
            dateSent = timestamp,
            type = SmsMessageType.SENT.code,
            threadId = SmsUtils.getOrCreateThreadId(context, phoneNumber),
            read = 1,
            status = SmsStatus.PENDING.code,
            serviceCenter = null,
            subscriptionId = SmsManager.getDefaultSmsSubscriptionId(),
            smsClassificationTypeId = 22,
            importanceScore = 2,
            confidenceScore = 1.0F,
            createdAtApp = timestamp,
            updatedAtApp = timestamp
        )
    }
}