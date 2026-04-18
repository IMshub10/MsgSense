package com.summer.notifai.banking_test_onxx

import android.content.Context
import java.io.InputStream

/**
 * ONNX banking-NER asset layout. All files live under [ASSET_SUBDIR].
 *
 * ONNX weights are **external**: ship [MODEL_FILE] and [MODEL_DATA_FILE]
 * together and load from a directory path (e.g. after copying to cache)
 * so ORT can resolve `.data` next to `.onnx`.
 *
 * The `tokenizer.json` loaded from here is consumed by the shared
 * `HfTokenizerBridge` in `com.summer.notifai.banking_ner`.
 */
object BankingOnnxAssets {

    const val ASSET_SUBDIR = "banking_test_onxx"

    const val MODEL_FILE = "model.onnx"
    /** External initializers for [MODEL_FILE]; required at runtime next to the `.onnx` file. */
    const val MODEL_DATA_FILE = "model.onnx.data"

    const val TOKENIZER_FILE = "tokenizer.json"
    const val TOKENIZER_CONFIG_FILE = "tokenizer_config.json"

    /** Hugging Face model config; includes `id2label` / `label2id` (no separate label_encoder.json). */
    const val CONFIG_FILE = "config.json"

    const val METADATA_FILE = "metadata.json"
    const val GOLDEN_TEST_FILE = "golden_test.json"

    fun modelPath(): String = "$ASSET_SUBDIR/$MODEL_FILE"
    fun modelDataPath(): String = "$ASSET_SUBDIR/$MODEL_DATA_FILE"
    fun tokenizerPath(): String = "$ASSET_SUBDIR/$TOKENIZER_FILE"
    fun tokenizerConfigPath(): String = "$ASSET_SUBDIR/$TOKENIZER_CONFIG_FILE"
    fun configPath(): String = "$ASSET_SUBDIR/$CONFIG_FILE"
    fun metadataPath(): String = "$ASSET_SUBDIR/$METADATA_FILE"
    fun goldenTestPath(): String = "$ASSET_SUBDIR/$GOLDEN_TEST_FILE"

    /** Opens any file under [ASSET_SUBDIR] (e.g. [MODEL_FILE], [GOLDEN_TEST_FILE]). */
    fun open(context: Context, fileName: String): InputStream =
        context.assets.open("$ASSET_SUBDIR/$fileName")
}
