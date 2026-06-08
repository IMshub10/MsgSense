package com.summer.notifai.banking_test_onxx

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.summer.notifai.banking_ner.BankingBioDecoder
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Stage 4 JVM unit test. Purely deterministic — no ML runtime needed.
 */
class BankingBioDecoderTest {

    data class GoldenTestCase(
        val sender: String,
        val body: String,
        val words: List<String>,
        val argmax: List<Int>,
        val word_ids: List<Int>,
        val word_labels: List<String>,
        val entities: Map<String, List<String>>,
    )

    private fun loadGoldenTests(): List<GoldenTestCase> {
        val goldenFile = LegacyNerTestPrerequisites.requireGolden()
        val type = object : TypeToken<List<GoldenTestCase>>() {}.type
        return Gson().fromJson(goldenFile.readText(), type)
    }

    @Test
    fun stage4_wordLabelsAndEntities_allCases() {
        val cases = loadGoldenTests()
        var passed = 0
        val failures = mutableListOf<String>()

        for ((index, tc) in cases.withIndex()) {
            val actualLabels = BankingBioDecoder.collapseToWordLabels(tc.argmax, tc.word_ids)
            val actualEntities = BankingBioDecoder.groupEntities(tc.words, actualLabels)

            val mismatches = mutableListOf<String>()

            if (actualLabels != tc.word_labels) {
                val maxLen = maxOf(actualLabels.size, tc.word_labels.size)
                val diffs = (0 until maxLen)
                    .filter { actualLabels.getOrNull(it) != tc.word_labels.getOrNull(it) }
                mismatches.add(
                    "  word_labels: ${diffs.size} diffs (sizes: expected=${tc.word_labels.size}, actual=${actualLabels.size})"
                )
                diffs.take(5).forEach { i ->
                    mismatches.add(
                        "    [$i] word=${tc.words.getOrNull(i)} expected=${tc.word_labels.getOrNull(i)} actual=${actualLabels.getOrNull(i)}"
                    )
                }
            }

            if (actualEntities != tc.entities) {
                val allKeys = (actualEntities.keys + tc.entities.keys).distinct()
                for (key in allKeys) {
                    val exp = tc.entities[key]
                    val act = actualEntities[key]
                    if (exp != act) {
                        mismatches.add("  entities[$key]: expected=$exp actual=$act")
                    }
                }
            }

            if (mismatches.isNotEmpty()) {
                val diff = buildString {
                    appendLine("FAIL Case $index (sender=${tc.sender}):")
                    appendLine("  body = ${tc.body}")
                    mismatches.forEach { appendLine(it) }
                }
                failures.add(diff)
                System.err.println(diff)
            } else {
                passed++
            }
        }

        println("Stage 4 BIO decoder: $passed/${cases.size} PASSED, ${failures.size} FAILED")
        if (failures.isNotEmpty()) {
            fail("${failures.size} case(s) failed. First failure:\n${failures.first()}")
        }
    }
}
