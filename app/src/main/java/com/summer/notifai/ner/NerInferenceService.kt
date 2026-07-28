package com.summer.notifai.ner

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.util.Log

class NerInferenceService : Service() {
    private lateinit var thread: HandlerThread
    private lateinit var messenger: Messenger
    private var runtime: NerMobileBertRuntime? = null

    override fun onCreate() {
        super.onCreate()
        thread = HandlerThread("ner-inference").apply { start() }
        messenger = Messenger(Handler(thread.looper, ::handleMessage))
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onUnbind(intent: Intent?): Boolean {
        Handler(thread.looper).post(::closeRuntime)
        return false
    }

    override fun onDestroy() {
        Handler(thread.looper).post {
            closeRuntime()
            thread.quitSafely()
        }
        super.onDestroy()
    }

    private fun handleMessage(message: Message): Boolean {
        if (message.what != NerIpc.MSG_EXTRACT) return false
        val reply = message.replyTo ?: return true
        val request = message.data
        val requestId = request.getString(NerIpc.KEY_REQUEST_ID).orEmpty()
        val smsId = request.getLong(NerIpc.KEY_SMS_ID)
        try {
            val result = (runtime ?: NerMobileBertRuntime(this).also { runtime = it }).extract(
                smsId = smsId,
                sender = requireNotNull(request.getString(NerIpc.KEY_SENDER)),
                body = requireNotNull(request.getString(NerIpc.KEY_BODY)),
            )
            val data = Bundle().apply {
                putString(NerIpc.KEY_REQUEST_ID, requestId)
                putLong(NerIpc.KEY_SMS_ID, result.smsId)
                putString(NerIpc.KEY_MODEL_ID, result.modelId)
                putString(NerIpc.KEY_MODEL_SHA256, result.modelSha256)
                putString(NerIpc.KEY_TOKENIZER_SHA256, result.tokenizerSha256)
                putString(NerIpc.KEY_PREPROCESSING_VERSION, result.preprocessingVersion)
                putString(NerIpc.KEY_PIPELINE_FINGERPRINT, result.pipelineFingerprint)
                putString(NerIpc.KEY_LABEL_SCHEMA_SHA256, result.labelSchemaSha256)
                putString(NerIpc.KEY_DECODER_VERSION, result.decoderVersion)
                putString(NerIpc.KEY_NORMALIZER_VERSION, result.normalizerVersion)
                putInt(NerIpc.KEY_TOKEN_COUNT, result.tokenCount)
                putBoolean(NerIpc.KEY_TRUNCATED, result.truncated)
                putDouble(NerIpc.KEY_INFERENCE_MS, result.inferenceMs)
                putParcelableArrayList(
                    NerIpc.KEY_ENTITIES,
                    ArrayList(result.entities.map { entity ->
                        Bundle().apply {
                            putString(NerIpc.KEY_ENTITY_TYPE, entity.type)
                            putString(NerIpc.KEY_RAW_TEXT, entity.rawText)
                            putString(NerIpc.KEY_NORMALIZED_VALUE, entity.normalizedValue)
                            putInt(NerIpc.KEY_START_OFFSET, entity.startOffset)
                            putInt(NerIpc.KEY_END_OFFSET, entity.endOffset)
                        }
                    })
                )
            }
            reply.send(Message.obtain(null, NerIpc.MSG_SUCCESS).apply { this.data = data })
        } catch (error: Throwable) {
            Log.e(TAG, "NER inference failed: ${error.javaClass.simpleName}")
            val data = Bundle().apply {
                putString(NerIpc.KEY_REQUEST_ID, requestId)
                putLong(NerIpc.KEY_SMS_ID, smsId)
                putString(NerIpc.KEY_FAILURE_CODE, error.javaClass.simpleName)
                putString(NerIpc.KEY_FAILURE_MESSAGE, error.message?.take(200))
            }
            runCatching {
                reply.send(Message.obtain(null, NerIpc.MSG_FAILURE).apply { this.data = data })
            }
        }
        return true
    }

    private fun closeRuntime() {
        runtime?.close()
        runtime = null
    }

    companion object {
        private const val TAG = "NerInferenceService"
    }
}
