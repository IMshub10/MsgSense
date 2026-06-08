package com.summer.notifai.banking_test_onxx

import org.junit.Assume.assumeTrue
import java.io.File

object LegacyNerTestPrerequisites {
    const val ASSET_DIR = "src/main/assets/banking_test_onxx"
    const val GOLDEN_PATH = "$ASSET_DIR/golden_test.json"
    const val RUST_LIB_DIR =
        "src/main/java/com/summer/notifai/banking_ner/rust_tokenizer/target/release"

    fun requireGolden(): File {
        val golden = File(GOLDEN_PATH)
        assumeTrue(
            "Legacy banking NER golden is ignored. Prepare $GOLDEN_PATH to run this test.",
            golden.isFile,
        )
        return golden
    }

    fun requireHostTokenizerLibrary(): File {
        val directory = File(RUST_LIB_DIR)
        val library = listOf(
            File(directory, "libhf_tokenizer_jni.dylib"),
            File(directory, "libhf_tokenizer_jni.so"),
        ).firstOrNull(File::isFile)
        assumeTrue(
            "Host tokenizer JNI library is absent. Build it in $RUST_LIB_DIR to run this test.",
            library != null,
        )
        return requireNotNull(library)
    }

    fun requireModelFiles(): Pair<File, File> {
        val model = File(ASSET_DIR, "model.onnx")
        val data = File(ASSET_DIR, "model.onnx.data")
        assumeTrue(
            "Legacy ONNX model files are absent. Prepare $ASSET_DIR to run this test.",
            model.isFile && data.isFile,
        )
        return model to data
    }
}
