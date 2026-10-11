package com.summer.ner

data class NerToken(
    val text: String,
    val bodyStart: Int?,
    val bodyEnd: Int?,
)

data class NerInput(
    val tokens: List<NerToken>,
    val prefixTokenCount: Int,
)

data class NerExtractedEntity(
    val type: String,
    val rawText: String,
    val normalizedValue: String?,
    val startOffset: Int,
    val endOffset: Int,
)

data class NerInferenceResult(
    val smsId: Long,
    val modelId: String,
    val modelSha256: String,
    val tokenizerSha256: String,
    val preprocessingVersion: String,
    val pipelineFingerprint: String,
    val labelSchemaSha256: String,
    val decoderVersion: String,
    val normalizerVersion: String,
    val tokenCount: Int,
    val truncated: Boolean,
    val inferenceMs: Double,
    val entities: List<NerExtractedEntity>,
)
