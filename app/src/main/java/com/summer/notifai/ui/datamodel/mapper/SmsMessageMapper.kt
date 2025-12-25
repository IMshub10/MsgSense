package com.summer.notifai.ui.datamodel.mapper

import com.summer.core.android.sms.util.SmsMessageType
import com.summer.core.data.local.model.SmsMessageModel
import com.summer.core.ui.model.SmsClassificationType
import com.summer.core.util.DateUtils

import com.summer.notifai.ui.datamodel.SmsClassificationDataModel
import com.summer.notifai.ui.datamodel.SmsMessageDataModel

object SmsMessageMapper {
    fun SmsMessageModel.toSmsMessageDataModel(): SmsMessageDataModel {
        return SmsMessageDataModel(
            id = id,
            androidSmsId = androidSmsId,
            message = body.orEmpty(),
            dateInEpoch = date,
            date = DateUtils.formatTimeOnly(date),
            isIncoming = SmsMessageType.isIncoming(type),
            status = status,
            smsClassificationDataModel = SmsClassificationDataModel(
                smsType = smsMessageType.orEmpty(),
                type = SmsClassificationType.fromCompactName(compactType.orEmpty())
                    ?: SmsClassificationType.IMPORTANT
            )
        )
    }
}