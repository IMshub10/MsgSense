package com.summer.ner.tokenizer

/**
 * JNI bridge to the Rust HuggingFace `tokenizers` crate.
 *
 * Shared between the ONNX and TFLite pipelines: both models were trained on
 * `albert-base-v2` and use the same `tokenizer.json`, so a single Rust
 * tokenizer instance serves both.
 *
 * The native library exposes three functions:
 * - [nativeLoadTokenizer] — load `tokenizer.json` from a file path
 * - [nativeEncode] — encode a pre-tokenized word list (is_pretokenized=true)
 * - [nativeFree] — release the tokenizer
 *
 * [nativeEncode] returns a flat IntArray of length 384:
 *   [input_ids(128), attention_mask(128), word_ids(128)]
 *
 * NOTE: because this object lives in package `com.summer.ner.tokenizer`,
 * the JNI symbols exported by Rust must be
 *   `Java_com_summer_ner_tokenizer_HfTokenizerBridge_*`
 * — renaming this package requires regenerating the native library.
 */
object HfTokenizerBridge {

    private var loaded = false

    /**
     * Load the native library by name (uses java.library.path).
     */
    fun loadLibrary() {
        if (!loaded) {
            System.loadLibrary("hf_tokenizer_jni")
            loaded = true
        }
    }

    /**
     * Load the native library by absolute path to the .dylib/.so file.
     * Useful for desktop/JVM unit tests where java.library.path isn't set.
     */
    fun loadLibraryFromPath(absolutePath: String) {
        if (!loaded) {
            System.load(absolutePath)
            loaded = true
        }
    }

    fun loadTokenizer(tokenizerJsonPath: String) {
        nativeLoadTokenizer(tokenizerJsonPath)
    }

    /**
     * Encodes a pre-tokenized word list and returns a [TokenizerResult].
     */
    fun encode(words: List<String>): TokenizerResult {
        val flat = nativeEncode(words.toTypedArray())
        val inputIds = flat.sliceArray(0 until 128)
        val attentionMask = flat.sliceArray(128 until 256)
        val wordIds = flat.sliceArray(256 until 384)
        return TokenizerResult(inputIds, attentionMask, wordIds)
    }

    fun free() {
        nativeFree()
    }

    data class TokenizerResult(
        val inputIds: IntArray,
        val attentionMask: IntArray,
        val wordIds: IntArray,
    )

    @JvmStatic
    private external fun nativeLoadTokenizer(path: String)

    @JvmStatic
    private external fun nativeEncode(words: Array<String>): IntArray

    @JvmStatic
    private external fun nativeFree()
}
