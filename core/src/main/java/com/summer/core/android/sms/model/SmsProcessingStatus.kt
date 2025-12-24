package com.summer.core.android.sms.model

sealed class SmsProcessingStatus(val key: String) {
    data object Loading : SmsProcessingStatus("LOADING")
    data object Success : SmsProcessingStatus("SUCCESS")
    data object Error : SmsProcessingStatus("ERROR")

    companion object {
        fun fromString(status: String?): SmsProcessingStatus = when (status) {
            Loading.key -> Loading
            Success.key -> Success
            Error.key -> Error
            else -> Loading
        }

        const val STATUS_KEY = "status"
        const val PROCESSED_COUNT_KEY = "processed"
        const val TOTAL_COUNT_KEY = "total"
        const val ERROR_MESSAGE_KEY = "errorMessage"
    }
}
