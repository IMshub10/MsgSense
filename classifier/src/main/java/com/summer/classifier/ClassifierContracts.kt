package com.summer.classifier

/**
 * Classification seam for this module. The concrete ONNX classifier (`SmsClassifierModel`)
 * implements it and is Hilt-bound, so callers depend only on this interface and never load
 * the model themselves.
 */
interface SmsClassifier {
    suspend fun classify(rawAddress: String, body: String): SmsClassification
}

data class SmsClassification(
    val importanceScore: Int,
    val smsClassificationTypeId: Int,
    val confidenceScore: Float,
)
