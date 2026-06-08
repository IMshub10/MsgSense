package com.summer.notifai.banking_test_onxx

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.summer.notifai.banking_ner.BankingPreTokenizer
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Stage 1 JVM unit test for the ONNX golden data.
 *
 * Runs on the JVM (not on-device) — validates that our Kotlin
 * pre-tokenizer matches the Python `predict.py` word list bundled
 * in `banking_test_onxx/golden_test.json`.
 */
class BankingPreTokenizerTest {

    data class GoldenTestCase(
        val sender: String,
        val body: String,
        val words: List<String>,
    )

    private fun loadGoldenTests(): List<GoldenTestCase> {
        val goldenFile = LegacyNerTestPrerequisites.requireGolden()
        val type = object : TypeToken<List<GoldenTestCase>>() {}.type
        return Gson().fromJson(goldenFile.readText(), type)
    }

    @Test
    fun stage1_preTokenizer_allCases() {
        val cases = loadGoldenTests()
        var passed = 0
        val failures = mutableListOf<String>()

        for ((index, tc) in cases.withIndex()) {
            val actual = BankingPreTokenizer.tokenize(tc.sender, tc.body)
            if (actual != tc.words) {
                val diff = buildString {
                    appendLine("FAIL Case $index (sender=${tc.sender}):")
                    appendLine("  body     = ${tc.body}")
                    appendLine("  expected = ${tc.words}")
                    appendLine("  actual   = $actual")
                    appendLine("  expected size = ${tc.words.size}, actual size = ${actual.size}")
                    val maxLen = maxOf(tc.words.size, actual.size)
                    for (i in 0 until maxLen) {
                        val exp = tc.words.getOrNull(i)
                        val act = actual.getOrNull(i)
                        if (exp != act) {
                            appendLine("  index $i: expected=$exp  actual=$act")
                        }
                    }
                }
                failures.add(diff)
                System.err.println(diff)
            } else {
                passed++
            }
        }

        println("Stage 1 pre-tokenizer: $passed/${cases.size} PASSED, ${failures.size} FAILED")
        if (failures.isNotEmpty()) {
            fail("${failures.size} case(s) failed. First failure:\n${failures.first()}")
        }
    }

    @Test
    fun stage1_senderCleaning() {
        assertEquals("HDFCBK", BankingPreTokenizer.cleanSender("HDFCBK"))
        assertEquals("HDFCBK", BankingPreTokenizer.cleanSender("AD-HDFCBK"))
        assertEquals("SBIBNK", BankingPreTokenizer.cleanSender("  SBIBNK  "))
        assertEquals("SBIBNK", BankingPreTokenizer.cleanSender("VM-SBIBNK"))
    }

    @Test
    fun stage1_regexKeepsDotCommaInTokens() {
        val tokens = BankingPreTokenizer.regexTokenize("Rs.698 spent 1,500.00")
        assertEquals(listOf("Rs.698", "spent", "1,500.00"), tokens)
    }

    @Test
    fun stage1_regexSplitsPunctuation() {
        val tokens = BankingPreTokenizer.regexTokenize("A/c XX4321 on 15-Mar-25")
        assertEquals(listOf("A", "/", "c", "XX4321", "on", "15", "-", "Mar", "-", "25"), tokens)
    }

    @Test
    fun stage1_regexSkipsNbspLikePython() {
        val nbsp = '\u00A0'
        val tokens = BankingPreTokenizer.regexTokenize("fraud:$nbsp https://x")
        assertEquals(listOf("fraud", ":", "https", ":", "/", "/", "x"), tokens)
    }
}
