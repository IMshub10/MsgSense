package com.summer.notifai.banking_test_tflite

import android.content.Context
import java.io.InputStream

/**
 * TFLite banking-NER asset layout. All files live under [ASSET_SUBDIR].
 *
 * Unlike the ONNX model, the TFLite model embeds all weights inside
 * [MODEL_FILE], so it can be loaded from a `ByteBuffer` directly — no need
 * to copy anything to cacheDir for inference.
 *
 * The `tokenizer.json` here is the same albert-base-v2 tokenizer used by
 * ONNX, so it's consumed by the shared `HfTokenizerBridge` in
 * `com.summer.notifai.banking_ner`.
 */
object BankingTfliteAssets {

    const val ASSET_SUBDIR = "banking_test_tflite"

    const val MODEL_FILE = "model.tflite"

    const val TOKENIZER_FILE = "tokenizer.json"
    const val TOKENIZER_CONFIG_FILE = "tokenizer_config.json"

    /** Newline-separated BIO labels (same order as model output). */
    const val LABELS_FILE = "labels.txt"

    /** Model card: labels, max_seq_length, input/output shapes. */
    const val MODEL_CARD_FILE = "model_card.json"


    const val GOLDEN_TEST_FILE = "golden_test.json"

    fun modelPath(): String = "$ASSET_SUBDIR/$MODEL_FILE"
    fun tokenizerPath(): String = "$ASSET_SUBDIR/$TOKENIZER_FILE"
    fun tokenizerConfigPath(): String = "$ASSET_SUBDIR/$TOKENIZER_CONFIG_FILE"
    fun labelsPath(): String = "$ASSET_SUBDIR/$LABELS_FILE"
    fun modelCardPath(): String = "$ASSET_SUBDIR/$MODEL_CARD_FILE"
    fun goldenTestPath(): String = "$ASSET_SUBDIR/$GOLDEN_TEST_FILE"

    /** Opens any file under [ASSET_SUBDIR]. */
    fun open(context: Context, fileName: String): InputStream =
        context.assets.open("$ASSET_SUBDIR/$fileName")
}
