package com.summer.core.domain.model

sealed class SmsBatchResult{
    data object Success : SmsBatchResult()
    data class Failure(val exception: Exception) : SmsBatchResult()
}