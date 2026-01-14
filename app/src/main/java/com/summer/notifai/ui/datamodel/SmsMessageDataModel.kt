package com.summer.notifai.ui.datamodel

import androidx.databinding.BaseObservable

data class SmsMessageDataModel(
    val id: Long,
    val androidSmsId: Long?,
    val message: String,
    val dateInEpoch: Long,
    val date: String,
    val isIncoming: Boolean,
    var status: Int?,
    val smsClassificationDataModel: SmsClassificationDataModel,
    var isSelected: Boolean = false
) : BaseObservable() {
    override fun equals(other: Any?): Boolean {
        return if (other is SmsMessageDataModel) {
            other.id == id
        } else {
            false
        }

    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}