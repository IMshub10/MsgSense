package com.summer.classifier.ml.model

data class SmsClassifierOutputModel(
    val importanceScore: Int,
    val smsClassificationTypeId: Int,
    val confidenceScore: Float,
    @Deprecated("use smsClassificationTypeId for better classification") val smsClassTypeId: Int,
    @Deprecated("use smsClassificationTypeId for better classification") val smsSubClassTypeId: Int,
)