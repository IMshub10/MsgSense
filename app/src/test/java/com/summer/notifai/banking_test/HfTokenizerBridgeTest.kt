package com.summer.notifai.banking_test

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class HfTokenizerBridgeTest {

    data class GoldenTestCase(
        val sender: String,
        val body: String,
        val words: List<String>,
        val input_ids: List<Int>,
        val attention_mask: List<Int>,
        val word_ids: List<Int>,
    )

    companion object {
        private const val RUST_LIB_DIR =
            "src/main/java/com/summer/notifai/banking_test/rust_tokenizer/target/release"
        private const val TOKENIZER_PATH =
            "src/main/assets/banking_test/tokenizer.json"

        @JvmStatic
        @BeforeClass
        fun setup() {
            val libDir = File(RUST_LIB_DIR)
            require(libDir.exists()) {
                "Rust library not built. Run:\n" +
                    "  cd $RUST_LIB_DIR/.. && CARGO_TARGET_DIR=./target cargo build --release"
            }
            val dylibFile = File(libDir, "libhf_tokenizer_jni.dylib")
            val soFile = File(libDir, "libhf_tokenizer_jni.so")
            val libFile = when {
                dylibFile.exists() -> dylibFile
                soFile.exists() -> soFile
                else -> error("Native lib not found in $libDir")
            }
            HfTokenizerBridge.loadLibraryFromPath(libFile.absolutePath)
            HfTokenizerBridge.loadTokenizer(File(TOKENIZER_PATH).absolutePath)
        }
    }

    private fun loadGoldenTests(): List<GoldenTestCase> {
        val goldenFile = File("src/main/assets/banking_test/golden_test.json")
        require(goldenFile.exists()) { "golden_test.json not found at ${goldenFile.absolutePath}" }
        val type = object : TypeToken<List<GoldenTestCase>>() {}.type
        return Gson().fromJson(goldenFile.readText(), type)
    }

    @Test
    fun stage2_tokenizer_allCases() {
        val cases = loadGoldenTests()
        var passed = 0
        val failures = mutableListOf<String>()

        for ((index, tc) in cases.withIndex()) {
            val result = HfTokenizerBridge.encode(tc.words)
            val actualIds = result.inputIds.toList()
            val actualMask = result.attentionMask.toList()
            val actualWordIds = result.wordIds.toList()

            val mismatches = mutableListOf<String>()

            if (actualIds != tc.input_ids) {
                val firstDiff = actualIds.zip(tc.input_ids).indexOfFirst { (a, e) -> a != e }
                mismatches.add(
                    "  input_ids: first diff at index $firstDiff" +
                        " (expected=${tc.input_ids.getOrNull(firstDiff)}, actual=${actualIds.getOrNull(firstDiff)})"
                )
            }
            if (actualMask != tc.attention_mask) {
                val firstDiff = actualMask.zip(tc.attention_mask).indexOfFirst { (a, e) -> a != e }
                mismatches.add(
                    "  attention_mask: first diff at index $firstDiff" +
                        " (expected=${tc.attention_mask.getOrNull(firstDiff)}, actual=${actualMask.getOrNull(firstDiff)})"
                )
            }
            if (actualWordIds != tc.word_ids) {
                val firstDiff = actualWordIds.zip(tc.word_ids).indexOfFirst { (a, e) -> a != e }
                mismatches.add(
                    "  word_ids: first diff at index $firstDiff" +
                        " (expected=${tc.word_ids.getOrNull(firstDiff)}, actual=${actualWordIds.getOrNull(firstDiff)})"
                )
            }

            if (mismatches.isNotEmpty()) {
                val diff = buildString {
                    appendLine("FAIL Case $index (sender=${tc.sender}):")
                    appendLine("  body = ${tc.body}")
                    appendLine("  words (${tc.words.size}): ${tc.words}")
                    mismatches.forEach { appendLine(it) }
                }
                failures.add(diff)
                System.err.println(diff)
            } else {
                passed++
            }
        }

        println("Stage 2 tokenizer: $passed/${cases.size} PASSED, ${failures.size} FAILED")
        if (failures.isNotEmpty()) {
            fail("${failures.size} case(s) failed. First failure:\n${failures.first()}")
        }
    }

    @Test
    fun stage_singleCase() {
        val cases = loadGoldenTests()
        val result = HfTokenizerBridge.encode(cases[0].words)

        assertTrue(result.wordIds.contentEquals(cases[0].word_ids.toIntArray()))
    }
}
