package com.summer.ner

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.summer.core.ner.NerEntityTypes
import com.summer.ner.tokenizer.BankingBioDecoder
import com.summer.ner.tokenizer.HfTokenizerBridge
import java.io.File
import java.nio.LongBuffer

class NerMobileBertRuntime(private val context: Context) : AutoCloseable {
    private val assetRoot = "ner_mobilebert"
    private val expectedHashes = context.assets.open("$assetRoot/manifest.sha256")
        .bufferedReader()
        .useLines { lines ->
            lines.filter { it.isNotBlank() }.associate { line ->
                val parts = line.trim().split(Regex("\\s+"), limit = 2)
                parts[1] to parts[0]
            }
        }
    private val model = materialize("model.onnx")
    private val tokenizer = materialize("tokenizer.json")
    private val environment = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply {
        setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
        setIntraOpNumThreads(1)
        setInterOpNumThreads(1)
    }
    private val session: OrtSession
    val modelSha256 = expectedHashes.getValue("model.onnx")
    val tokenizerSha256 = expectedHashes.getValue("tokenizer.json")

    init {
        HfTokenizerBridge.loadLibrary()
        HfTokenizerBridge.loadTokenizer(tokenizer.absolutePath)
        session = environment.createSession(model.absolutePath, options)
    }

    fun extract(smsId: Long, sender: String, body: String): NerInferenceResult {
        val input = NerPreprocessor.preprocess(sender, body)
        val tokenized = HfTokenizerBridge.encode(input.tokens.map { it.text })
        val lastEncodedWord = tokenized.wordIds.filter { it >= 0 }.maxOrNull() ?: -1
        val truncated = lastEncodedWord < input.tokens.lastIndex
        val tensors = mutableMapOf<String, OnnxTensor>()
        fun tensor(values: IntArray) = OnnxTensor.createTensor(
            environment,
            LongBuffer.wrap(LongArray(values.size) { values[it].toLong() }),
            longArrayOf(1, values.size.toLong()),
        )
        tensors["input_ids"] = tensor(tokenized.inputIds)
        tensors["attention_mask"] = tensor(tokenized.attentionMask)
        tensors["token_type_ids"] = tensor(IntArray(tokenized.inputIds.size))
        val started = System.nanoTime()
        val argmax = try {
            session.run(tensors).use { result ->
                @Suppress("UNCHECKED_CAST")
                val logits = (result[0].value as Array<Array<FloatArray>>)[0]
                logits.map { row -> row.indices.maxByOrNull { row[it] }!! }
            }
        } finally {
            tensors.values.forEach(OnnxTensor::close)
        }
        val inferenceMs = (System.nanoTime() - started) / 1_000_000.0
        val labels = BankingBioDecoder.collapseToWordLabels(argmax, tokenized.wordIds.toList())
        val entities = decodeBodyEntities(body, input.tokens, labels)
        return NerInferenceResult(
            smsId, MODEL_ID, modelSha256, tokenizerSha256, NerPreprocessor.VERSION,
            NerPipelineMetadata.PIPELINE_FINGERPRINT,
            NerPipelineMetadata.LABEL_SCHEMA_SHA256,
            NerPipelineMetadata.DECODER_VERSION,
            NerPipelineMetadata.NORMALIZER_VERSION,
            input.tokens.size, truncated, inferenceMs, entities,
        )
    }

    private fun decodeBodyEntities(body: String, tokens: List<NerToken>, labels: List<String>): List<NerExtractedEntity> {
        val entities = mutableListOf<NerExtractedEntity>()
        var start = -1
        var type: String? = null
        fun flush(end: Int) {
            val currentType = type
            if (start >= 0 && currentType != null) {
                if (tokens[start].bodyStart == null) {
                    start = -1
                    type = null
                    return
                }
                val group = tokens.subList(start, end).filter { it.bodyStart != null }
                if (group.isNotEmpty()) {
                    val startOffset = requireNotNull(group.first().bodyStart)
                    val endOffset = requireNotNull(group.last().bodyEnd)
                    val raw = body.substring(startOffset, endOffset)
                    val entityType = NerEntityTypes.requireKnown(currentType)
                    entities += NerExtractedEntity(
                        entityType, raw, NerNormalizer.normalize(entityType, raw),
                        startOffset, endOffset,
                    )
                }
            }
            start = -1
            type = null
        }
        for (index in 0..labels.size) {
            val label = labels.getOrElse(index) { "O" }
            when {
                label.startsWith("B-") -> {
                    flush(index)
                    start = index
                    type = label.removePrefix("B-")
                }
                label.startsWith("I-") && label.removePrefix("I-") == type -> Unit
                else -> flush(index)
            }
        }
        return entities
    }

    private fun materialize(name: String): File {
        val directory = File(context.cacheDir, assetRoot).apply { mkdirs() }
        return File(directory, name).also { target ->
            val expected = expectedHashes.getValue(name)
            val marker = File(directory, "$name.sha256")
            if (!target.isFile || marker.readTextOrNull() != expected) {
                context.assets.open("$assetRoot/$name").use { input ->
                    target.outputStream().use(input::copyTo)
                }
                marker.writeText(expected)
            }
        }
    }

    private fun File.readTextOrNull(): String? = runCatching { readText() }.getOrNull()

    override fun close() {
        session.close()
        options.close()
        HfTokenizerBridge.free()
    }

    companion object {
        const val MODEL_ID = "mobilebert-v50"
        const val MAX_LENGTH = 128
    }
}
