package com.summer.notifai.ner

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class NerServiceClient(private val context: Context) : AutoCloseable {
    @Volatile private var remote: Messenger? = null
    @Volatile private var bound = false
    @Volatile private var connectContinuation: CancellableContinuation<Unit>? = null
    @Volatile private var responseContinuation: CancellableContinuation<NerInferenceResult>? = null
    @Volatile private var activeRequestId: String? = null

    private val reply = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (message.data.getString(NerIpc.KEY_REQUEST_ID) != activeRequestId) return@Handler true
        val continuation = responseContinuation
        responseContinuation = null
        activeRequestId = null
        if (continuation?.isActive != true) return@Handler true
        when (message.what) {
            NerIpc.MSG_SUCCESS -> continuation.resume(message.data.toResult())
            NerIpc.MSG_FAILURE -> continuation.resumeWithException(
                NerRemoteException(
                    message.data.getString(NerIpc.KEY_FAILURE_CODE).orEmpty(),
                    message.data.getString(NerIpc.KEY_FAILURE_MESSAGE),
                )
            )
        }
        true
    })

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            bound = true
            remote = Messenger(service)
            connectContinuation?.takeIf { it.isActive }?.resume(Unit)
            connectContinuation = null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remote = null
            bound = false
            failActive(NerRemoteException("SERVICE_DISCONNECTED", null))
        }

        override fun onBindingDied(name: ComponentName?) = onServiceDisconnected(name)
    }

    suspend fun extract(smsId: Long, sender: String, body: String): NerInferenceResult {
        ensureBound()
        return suspendCancellableCoroutine { continuation ->
            check(responseContinuation == null) { "NER service client only supports one active request" }
            val requestId = UUID.randomUUID().toString()
            activeRequestId = requestId
            responseContinuation = continuation
            continuation.invokeOnCancellation {
                responseContinuation = null
                activeRequestId = null
            }
            val request = Message.obtain(null, NerIpc.MSG_EXTRACT).apply {
                replyTo = reply
                data = Bundle().apply {
                    putString(NerIpc.KEY_REQUEST_ID, requestId)
                    putLong(NerIpc.KEY_SMS_ID, smsId)
                    putString(NerIpc.KEY_SENDER, sender)
                    putString(NerIpc.KEY_BODY, body)
                }
            }
            runCatching { requireNotNull(remote).send(request) }
                .onFailure {
                    responseContinuation = null
                    activeRequestId = null
                    continuation.resumeWithException(it)
                }
        }
    }

    private suspend fun ensureBound() {
        if (remote != null) return
        suspendCancellableCoroutine { continuation ->
            connectContinuation = continuation
            continuation.invokeOnCancellation { connectContinuation = null }
            val started = context.bindService(
                Intent(context, NerInferenceService::class.java),
                connection,
                Context.BIND_AUTO_CREATE,
            )
            if (!started) {
                connectContinuation = null
                continuation.resumeWithException(NerRemoteException("BIND_FAILED", null))
            }
        }
    }

    private fun failActive(error: Throwable) {
        connectContinuation?.takeIf { it.isActive }?.resumeWithException(error)
        connectContinuation = null
        responseContinuation?.takeIf { it.isActive }?.resumeWithException(error)
        responseContinuation = null
        activeRequestId = null
    }

    override fun close() {
        failActive(NerRemoteException("CLIENT_CLOSED", null))
        if (bound) runCatching { context.unbindService(connection) }
        bound = false
        remote = null
    }

    @Suppress("DEPRECATION")
    private fun Bundle.toResult(): NerInferenceResult {
        val entityBundles = getParcelableArrayList<Bundle>(NerIpc.KEY_ENTITIES).orEmpty()
        return NerInferenceResult(
            smsId = getLong(NerIpc.KEY_SMS_ID),
            modelId = requireNotNull(getString(NerIpc.KEY_MODEL_ID)),
            modelSha256 = requireNotNull(getString(NerIpc.KEY_MODEL_SHA256)),
            tokenizerSha256 = requireNotNull(getString(NerIpc.KEY_TOKENIZER_SHA256)),
            preprocessingVersion = requireNotNull(getString(NerIpc.KEY_PREPROCESSING_VERSION)),
            tokenCount = getInt(NerIpc.KEY_TOKEN_COUNT),
            truncated = getBoolean(NerIpc.KEY_TRUNCATED),
            inferenceMs = getDouble(NerIpc.KEY_INFERENCE_MS),
            entities = entityBundles.map {
                NerExtractedEntity(
                    type = requireNotNull(it.getString(NerIpc.KEY_ENTITY_TYPE)),
                    rawText = requireNotNull(it.getString(NerIpc.KEY_RAW_TEXT)),
                    normalizedValue = it.getString(NerIpc.KEY_NORMALIZED_VALUE),
                    startOffset = it.getInt(NerIpc.KEY_START_OFFSET),
                    endOffset = it.getInt(NerIpc.KEY_END_OFFSET),
                )
            },
        )
    }
}

class NerRemoteException(val code: String, message: String?) : RuntimeException(message)
