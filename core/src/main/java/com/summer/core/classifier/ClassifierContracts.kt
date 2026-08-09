package com.summer.core.classifier

/**
 * Classification seam owned by `:core`. The concrete ONNX classifier (`SmsClassifierModel`)
 * implements this and is Hilt-bound; `:core` callers (`SmsInserter`) and the batch pipeline
 * depend only on this interface so the model can later move to a `:classifier` module without
 * a reverse dependency.
 */
interface SmsClassifier {
    suspend fun classify(rawAddress: String, body: String): SmsClassification
}

data class SmsClassification(
    val importanceScore: Int,
    val smsClassificationTypeId: Int,
    val confidenceScore: Float,
)
