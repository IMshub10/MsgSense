package com.summer.core.ml.tokenizer

import android.content.Context
import android.util.Log
import java.io.File

object HfTokenizerBridge {
    private const val TAG = "HfTokenizerBridge"
    private const val TOKENIZER_ASSET_PATH = "ml/tokenizer.json"
    private const val TOKENIZER_CACHE_FILE = "albert_tokenizer.json"

    @Volatile
    private var isLoaded = false

    init {
        try {
            System.loadLibrary("hf_tokenizer_jni")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load hf_tokenizer_jni", t)
        }
    }

    @JvmStatic
    fun encodeToModelInputs(
        context: Context,
        text: String,
        maxLength: Int,
    ): Array<LongArray>? {
        if (!ensureLoaded(context)) return null
        return try {
            val encoded = nativeEncode(arrayOf(text), maxLength)
            if (encoded.isEmpty()) {
                Log.e(TAG, "nativeEncode returned empty result")
                null
            } else {
                val inputIds = when {
                    encoded.size >= maxLength -> encoded.copyOfRange(0, maxLength).toLongArrayCompat()
                    else -> return null
                }
                val attentionMask = if (encoded.size >= (2 * maxLength)) {
                    encoded.copyOfRange(maxLength, 2 * maxLength).toLongArrayCompat()
                } else {
                    LongArray(maxLength) { i -> if (inputIds[i] != 0L) 1L else 0L }
                }
                val tokenTypeIds = if (encoded.size >= (3 * maxLength)) {
                    encoded.copyOfRange(2 * maxLength, 3 * maxLength).toLongArrayCompat()
                } else {
                    LongArray(maxLength) { 0L }
                }
                arrayOf(inputIds, attentionMask, tokenTypeIds)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "nativeEncode failed", t)
            null
        }
    }

    @Synchronized
    private fun ensureLoaded(context: Context): Boolean {
        if (isLoaded) return true
        return try {
            val tokenizerPath = ensureTokenizerJson(context).absolutePath
            nativeLoadTokenizer(tokenizerPath)
            isLoaded = true
            Log.i(TAG, "Tokenizer loaded from $tokenizerPath")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "nativeLoadTokenizer failed", t)
            false
        }
    }

    private fun ensureTokenizerJson(context: Context): File {
        val outFile = File(context.filesDir, TOKENIZER_CACHE_FILE)
        if (outFile.exists() && outFile.length() > 0) return outFile

        context.assets.open(TOKENIZER_ASSET_PATH).use { input ->
            outFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return outFile
    }

    private external fun nativeLoadTokenizer(tokenizerPath: String)
    private external fun nativeEncode(texts: Array<String>, maxLength: Int): IntArray
    private external fun nativeFree()

    private fun IntArray.toLongArrayCompat(): LongArray {
        return LongArray(size) { this[it].toLong() }
    }
}
