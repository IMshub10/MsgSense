package com.summer.core.android.sms.model

sealed class SmsProcessingError(val userMessage: String) {
    data object PermissionDenied : SmsProcessingError("SMS access permission is required")
    data object NetworkError : SmsProcessingError("Please check your internet connection")
    data object SystemError : SmsProcessingError("Unable to process messages at the moment")
    data object StorageError : SmsProcessingError("Not enough storage space available")
    data object Cancelled : SmsProcessingError("SMS processing cancelled")
    data object UnknownError : SmsProcessingError("Something went wrong, please try again")

    companion object {
        fun fromException(exception: Throwable): SmsProcessingError {
            return when (exception) {
                is SecurityException -> PermissionDenied
                is IllegalStateException -> SystemError
                is OutOfMemoryError -> StorageError
                is InterruptedException -> Cancelled
                else -> UnknownError
            }
        }

        fun fromWorkInfo(state: androidx.work.WorkInfo.State): SmsProcessingError? {
            return when (state) {
                androidx.work.WorkInfo.State.CANCELLED -> Cancelled
                androidx.work.WorkInfo.State.FAILED -> SystemError
                else -> null
            }
        }
    }
}
