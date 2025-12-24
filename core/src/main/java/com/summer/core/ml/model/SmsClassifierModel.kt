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
import com.summer.core.ml.util.Constants.PADDING_TOKEN
import com.summer.core.ml.util.Constants.SEPARATOR
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
    private var ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var ortSession: OrtSession
    private var vocab: Map<String, Long>
    private var invVocab: Map<Long, String> // Inverse vocab for debugging
    private var labelMap: Map<Int, String>
    private var wordPieceTokenizer: WordPieceTokenizer

    init {
        val modelBuffer = loadModelFile(context)
        ortSession = ortEnv.createSession(modelBuffer, OrtSession.SessionOptions())

        // Load Tokenizer Vocab
        val vocabJson = loadFullJson(context)
        vocab = vocabJson.getJSONObject(VOCAB).toMapLong()
        invVocab = vocabJson.getJSONObject(INVERSE_VOCAB).toMapString()

        // Tokenizer
        wordPieceTokenizer = WordPieceTokenizer(vocab)

        // Load Label Encoder
        labelMap = loadLabelMap(context)
    }

    private fun loadModelFile(context: Context): ByteBuffer {
        val inputStream: InputStream = context.assets.open("ml/$MODEL_FILE_NAME")
        val byteArray = inputStream.readBytes()
        inputStream.close()

        // Allocate a direct ByteBuffer
        val byteBuffer = ByteBuffer.allocateDirect(byteArray.size)
        byteBuffer.order(ByteOrder.nativeOrder()) // Set native byte order
        byteBuffer.put(byteArray)
        byteBuffer.flip() // Reset position to 0 for reading
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

    /**
     * returns string in format Sender:senderId | Message:messageBody
     */
    private fun getInputTextFromSenderNMessage(sender: String, message: String): String {
        return "$MESSAGE_SENDER: $sender $SEPARATOR $MESSAGE: $message"
    }

    /**
     * Runs ONNX inference and returns label + confidence score
     */
    fun classifySms(sender: String, message: String): SmsClassifierOutputModel {
        val inputText = getInputTextFromSenderNMessage(sender, message)
        val inputTokens = wordPieceTokenizer.tokenize(inputText, maxLength = 128)
        val attentionMask =
            inputTokens.map { if (it != vocab[PADDING_TOKEN]) 1L else 0L }.toLongArray()

        val inputTensor =
            OnnxTensor.createTensor(ortEnv, LongBuffer.wrap(inputTokens), longArrayOf(1, 128))
        val attentionTensor =
            OnnxTensor.createTensor(ortEnv, LongBuffer.wrap(attentionMask), longArrayOf(1, 128))

        val modelInputs = mutableMapOf<String, OnnxTensor>(
            INPUT_IDS to inputTensor, ATTENTION_MASK to attentionTensor
        )
        val results = ortSession.run(modelInputs)

        val logits = (results[0].value as Array<FloatArray>)[0]
        val probabilities = softmax(logits)

        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] }!!
        val predictedLabel = labelMap[maxIndex] ?: "Unknown"
        val confidenceScore = probabilities[maxIndex].roundToTwoDecimalPlaces()

        return SmsClassifierOutputModel(
            importanceScore = predictedLabel.first().digitToIntOrNull() ?: 0,
            smsClassificationTypeId = predictedLabel.takeLast(2).toInt(),
            confidenceScore = confidenceScore,
            smsClassTypeId = predictedLabel[1].digitToIntOrNull() ?: 0,
            smsSubClassTypeId = predictedLabel[2].digitToIntOrNull() ?: 0
        )
    }

    /**
     * Computes Softmax on logits
     */
    private fun softmax(logits: FloatArray): FloatArray {
        val maxLogit = logits.maxOrNull()!!
        val expLogits = logits.map { exp((it - maxLogit).toDouble()).toFloat() }
        val sumExpLogits = expLogits.sum()
        return expLogits.map { it / sumExpLogits }.toFloatArray()
    }

    companion object {
        fun isEnoughMemoryAvailable(context: Context): Boolean {
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager?.getMemoryInfo(memoryInfo)

            val availableRam = memoryInfo.availMem / (1024 * 1024) // Convert to MB
            val totalRam = memoryInfo.totalMem / (1024 * 1024) // Convert to MB

            Log.d("OnnxModel", "Available RAM: ${availableRam}MB, Total RAM: ${totalRam}MB")

            return availableRam > 200 // Require at least **200MB free RAM** to load the model
        }
    }
}
