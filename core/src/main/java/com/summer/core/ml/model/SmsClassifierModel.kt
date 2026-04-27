package com.summer.core.ml.model

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.summer.core.ml.util.Constants.ATTENTION_MASK
import com.summer.core.ml.util.Constants.INPUT_IDS
import com.summer.core.ml.util.Constants.INVERSE_VOCAB
import com.summer.core.ml.util.Constants.LABEL_ENCODER_FILE_NAME
import com.summer.core.ml.util.Constants.MESSAGE
import com.summer.core.ml.util.Constants.MESSAGE_SENDER
import com.summer.core.ml.util.Constants.MODEL_FILE_NAME
import com.summer.core.ml.util.Constants.MODEL_VERSION
import com.summer.core.ml.util.Constants.PADDING_TOKEN
import com.summer.core.ml.util.Constants.SEPARATOR
import com.summer.core.ml.util.Constants.TOKEN_TYPE_IDS
import com.summer.core.ml.util.Constants.TOKENIZER_FILE_NAME
import com.summer.core.ml.util.Constants.VOCAB
import com.summer.core.ml.tokenizer.WordPieceTokenizer
import com.summer.core.util.roundToTwoDecimalPlaces
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.LongBuffer
import javax.inject.Singleton
import kotlin.math.exp

@Singleton
class SmsClassifierModel(@ApplicationContext context: Context) {
    private val appContext = context.applicationContext
    private val logTag = "SmsClassifierModel"
    private var ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var ortSession: OrtSession
    private var vocab: Map<String, Long>
    private var invVocab: Map<Long, String>
    private var labelMap: Map<Int, String>
    private var wordPieceTokenizer: WordPieceTokenizer
    private val padTokenId: Long
    private val usesTokenTypeIds: Boolean

    init {
        val modelBuffer = loadModelFile(appContext)
        ortSession = ortEnv.createSession(modelBuffer, OrtSession.SessionOptions())

        val vocabJson = loadFullJson(appContext)
        vocab = vocabJson.getJSONObject(VOCAB).toMapLong()
        invVocab = vocabJson.getJSONObject(INVERSE_VOCAB).toMapString()

        wordPieceTokenizer = WordPieceTokenizer(vocab)
        padTokenId = vocab[PADDING_TOKEN] ?: vocab["<pad>"] ?: error("Missing pad token in tokenizer vocab")

        labelMap = loadLabelMap(appContext)
        usesTokenTypeIds = ortSession.inputInfo.containsKey(TOKEN_TYPE_IDS)
        Log.i(
            logTag,
            "Loaded ml_model=$MODEL_VERSION file=$MODEL_FILE_NAME token_type_ids=$usesTokenTypeIds inputs=${ortSession.inputInfo.keys}"
        )
    }

    private fun loadModelFile(context: Context): ByteBuffer {
        val inputStream: InputStream = context.assets.open("ml/$MODEL_FILE_NAME")
        val byteArray = inputStream.readBytes()
        inputStream.close()

        val byteBuffer = ByteBuffer.allocateDirect(byteArray.size)
        byteBuffer.order(ByteOrder.nativeOrder())
        byteBuffer.put(byteArray)
        byteBuffer.flip()
        return byteBuffer
    }

    private fun loadFullJson(context: Context): JSONObject {
        val jsonStr =
            context.assets.open("ml/$TOKENIZER_FILE_NAME").bufferedReader().use { it.readText() }
        return JSONObject(jsonStr)
    }

    private fun JSONObject.toMapLong(): Map<String, Long> {
        val map = mutableMapOf<String, Long>()
        keys().forEach { key -> map[key] = getLong(key) }
        return map
    }

    private fun JSONObject.toMapString(): Map<Long, String> {
        val map = mutableMapOf<Long, String>()
        keys().forEach { key -> map[key.toLong()] = getString(key) }
        return map
    }

    private fun loadLabelMap(context: Context): Map<Int, String> {
        val jsonStr =
            context.assets.open("ml/$LABEL_ENCODER_FILE_NAME").bufferedReader()
                .use { it.readText() }
        val jsonObject = JSONObject(jsonStr)
        val map = mutableMapOf<Int, String>()
        jsonObject.keys().forEach { key -> map[key.toInt()] = jsonObject.getString(key) }
        return map
    }

    private fun getInputTextFromSenderNMessage(sender: String, message: String): String {
        return "$MESSAGE_SENDER: $sender $SEPARATOR $MESSAGE: $message"
    }

    fun classifySms(sender: String, message: String): SmsClassifierOutputModel {
        val inputText = getInputTextFromSenderNMessage(sender, message)
        val inputTokens = wordPieceTokenizer.tokenize(inputText, maxLength = 128)
        val attentionMask = inputTokens.map { if (it != padTokenId) 1L else 0L }.toLongArray()
        val tokenTypeIds = LongArray(128) { 0L }

        val inputTensor = OnnxTensor.createTensor(ortEnv, LongBuffer.wrap(inputTokens), longArrayOf(1, 128))
        val attentionTensor = OnnxTensor.createTensor(ortEnv, LongBuffer.wrap(attentionMask), longArrayOf(1, 128))
        val tokenTypeTensor = if (usesTokenTypeIds) {
            OnnxTensor.createTensor(ortEnv, LongBuffer.wrap(tokenTypeIds), longArrayOf(1, 128))
        } else {
            null
        }

        inputTensor.use { ids ->
            attentionTensor.use { mask ->
                tokenTypeTensor.use { typeIds ->
                    val modelInputs = mutableMapOf<String, OnnxTensor>(
                        INPUT_IDS to ids,
                        ATTENTION_MASK to mask
                    )
                    if (typeIds != null) {
                        modelInputs[TOKEN_TYPE_IDS] = typeIds
                    }

                    ortSession.run(modelInputs).use { results ->
                        val logits = (results[0].value as Array<FloatArray>)[0]
                        val probabilities = softmax(logits)

                        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0
                        val predictedLabel = labelMap[maxIndex] ?: "0_0"
                        val confidenceScore = probabilities[maxIndex].roundToTwoDecimalPlaces()
                        val parsed = parseLabel(predictedLabel)

                        return SmsClassifierOutputModel(
                            importanceScore = parsed.importanceScore,
                            smsClassificationTypeId = parsed.smsTypeId,
                            confidenceScore = confidenceScore,
                            smsClassTypeId = parsed.smsClassTypeId,
                            smsSubClassTypeId = parsed.smsSubClassTypeId
                        )
                    }
                }
            }
        }
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val maxLogit = logits.maxOrNull()!!
        val expLogits = logits.map { exp((it - maxLogit).toDouble()).toFloat() }
        val sumExpLogits = expLogits.sum()
        return expLogits.map { it / sumExpLogits }.toFloatArray()
    }

    private fun parseLabel(rawLabel: String): ParsedLabel {
        rawLabel.split("_").takeIf { it.size == 2 }?.let { parts ->
            val importance = parts[0].toIntOrNull()
            val smsTypeId = parts[1].toIntOrNull()
            if (importance != null && smsTypeId != null) {
                return ParsedLabel(
                    importanceScore = importance,
                    smsTypeId = smsTypeId,
                    smsClassTypeId = smsTypeId / 10,
                    smsSubClassTypeId = smsTypeId % 10
                )
            }
        }

        val digitsOnly = rawLabel.filter(Char::isDigit)
        if (digitsOnly.length >= 3) {
            val importance = digitsOnly.first().digitToInt()
            val smsTypeId = digitsOnly.takeLast(2).toInt()
            val smsClass = digitsOnly.getOrNull(1)?.digitToIntOrNull() ?: (smsTypeId / 10)
            val smsSubClass = digitsOnly.getOrNull(2)?.digitToIntOrNull() ?: (smsTypeId % 10)
            return ParsedLabel(
                importanceScore = importance,
                smsTypeId = smsTypeId,
                smsClassTypeId = smsClass,
                smsSubClassTypeId = smsSubClass
            )
        }

        return ParsedLabel(
            importanceScore = 0,
            smsTypeId = 0,
            smsClassTypeId = 0,
            smsSubClassTypeId = 0
        )
    }

    private data class ParsedLabel(
        val importanceScore: Int,
        val smsTypeId: Int,
        val smsClassTypeId: Int,
        val smsSubClassTypeId: Int
    )

    companion object {
        fun isEnoughMemoryAvailable(context: Context): Boolean {
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager?.getMemoryInfo(memoryInfo)

            val availableRam = memoryInfo.availMem / (1024 * 1024)
            val totalRam = memoryInfo.totalMem / (1024 * 1024)

            Log.d("OnnxModel", "Available RAM: ${availableRam}MB, Total RAM: ${totalRam}MB")

            return availableRam > 200
        }
    }
}
