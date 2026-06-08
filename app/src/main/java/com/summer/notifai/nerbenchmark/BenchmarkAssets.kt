package com.summer.notifai.nerbenchmark

import android.content.Context
import java.io.File
import java.security.MessageDigest

class BenchmarkAssets(private val context: Context) {
    companion object {
        const val ROOT = "ner_benchmark"
        const val MODEL = "model.onnx"
        const val TOKENIZER = "tokenizer.json"
        const val FIXTURE = "fixture.jsonl"
        const val GOLDEN = "golden.jsonl"
        const val PERFORMANCE_SELECTION = "performance-selection.json"
        const val MANIFEST = "manifest.sha256"
    }

    fun verifyAndMaterialize(): MaterializedAssets {
        val expected = context.assets.open("$ROOT/$MANIFEST").bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.associate { line ->
                val parts = line.trim().split(Regex("\\s+"), limit = 2)
                parts[1].removePrefix("*") to parts[0]
            }
        }
        val directory = File(context.cacheDir, "ner_benchmark_assets").apply { mkdirs() }
        val files = listOf(MODEL, TOKENIZER, FIXTURE, GOLDEN, PERFORMANCE_SELECTION).associateWith { name ->
            File(directory, name).also { target ->
                context.assets.open("$ROOT/$name").use { input ->
                    target.outputStream().use(input::copyTo)
                }
                check(expected[name] == sha256(target)) { "SHA-256 mismatch for $name" }
            }
        }
        return MaterializedAssets(
            model = files.getValue(MODEL),
            tokenizer = files.getValue(TOKENIZER),
            fixture = files.getValue(FIXTURE),
            golden = files.getValue(GOLDEN),
            performanceSelection = files.getValue(PERFORMANCE_SELECTION),
            hashes = expected,
        )
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

data class MaterializedAssets(
    val model: File,
    val tokenizer: File,
    val fixture: File,
    val golden: File,
    val performanceSelection: File,
    val hashes: Map<String, String>,
)
