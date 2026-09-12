package com.summer.ner.tokenizer

import java.util.regex.Pattern

/**
 * Stage 1 of the banking NER pipeline: sender cleaning + regex word tokenization.
 *
 * Runtime-agnostic (no ONNX / TFLite dependency). Both the ONNX and TFLite
 * pipelines use this exact stage, so it lives in the shared `banking_ner`
 * package.
 *
 * Produces a word list identical to the Python reference (`predict.py`).
 *
 * Android's Pattern doesn't support UNICODE_CHARACTER_CLASS, so we use [\s\p{Z}]
 * to match both ASCII whitespace and Unicode separators (e.g. U+00A0 no-break space)
 * the same way Python 3 `re.finditer` does with `\s`.
 */
object BankingPreTokenizer {

    private val WORD_PATTERN = Pattern.compile(
        """([A-Za-z0-9]+(?:[.,][A-Za-z0-9]+)*)|([^A-Za-z0-9\s\p{Z}])""",
    )

    /**
     * Cleans sender, builds the standard prefix, regex-tokenizes both prefix and body,
     * and returns the concatenated word list.
     */
    fun tokenize(sender: String, body: String): List<String> {
        val cleaned = cleanSender(sender)
        val prefix = "SenderAddressId : $cleaned Body :"
        val prefixWords = regexTokenize(prefix)
        val bodyWords = regexTokenize(body)
        return prefixWords + bodyWords
    }

    /**
     * If the 3rd character (index 2) is '-', drop the first 3 characters.
     * Otherwise return trimmed as-is.
     */
    internal fun cleanSender(sender: String): String {
        val trimmed = sender.trim()
        return if (trimmed.length > 2 && trimmed[2] == '-') {
            trimmed.substring(3)
        } else {
            trimmed
        }
    }

    /**
     * Regex word tokenization matching the Python pattern:
     * - Alphanumeric runs (optionally joined by `.` or `,`) → one token
     * - Every non-alphanumeric, non-whitespace char → its own token
     * - Whitespace is consumed / skipped
     */
    internal fun regexTokenize(text: String): List<String> {
        val m = WORD_PATTERN.matcher(text)
        val out = ArrayList<String>()
        while (m.find()) {
            out.add(m.group())
        }
        return out
    }
}
