package com.summer.ner

/** Inference boundary shared by the production IPC client and coordinator regression tests. */
interface NerExtractionClient : AutoCloseable {
    suspend fun extract(smsId: Long, sender: String, body: String): NerInferenceResult
}

fun interface NerClientFactory {
    fun create(): NerExtractionClient
}
