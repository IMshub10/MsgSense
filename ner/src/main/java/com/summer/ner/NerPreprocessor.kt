package com.summer.ner

object NerPreprocessor {
    const val VERSION = "msgsense-v50-1"
    private val senderSuffix = Regex("-[A-Z]$")
    private val digitDotAlpha = Regex("^(.+[\\d)])\\.([A-Za-z].+)$")
    private val sentenceGlue = Regex("^(.+[a-zA-Z])\\.([A-Z][a-zA-Z]*)$")
    private val currencyAmount = Regex("^(INR|Rs\\.?|USD|EUR|GBP|₹|\\$|€)([\\d]+(?:[.,]\\d+)*)$", RegexOption.IGNORE_CASE)
    private val splitPunctuation = charArrayOf('-', '!', '*', ':', '+', '?', '_', '(', '/')

    fun preprocess(sender: String, body: String): NerInput {
        val cleanedSender = senderSuffix.replace(sender, "")
        val prefix = tokenize("SenderAddressId : $cleanedSender Body :", bodyRelative = false)
        val bodyTokens = tokenize(body, bodyRelative = true)
        return NerInput(prefix + bodyTokens, prefix.size)
    }

    fun tokenize(text: String, bodyRelative: Boolean = true): List<NerToken> {
        val result = mutableListOf<NerToken>()
        Regex("\\S+").findAll(text).forEach { match ->
            splitRaw(result, match.value, match.range.first, bodyRelative)
        }
        return result
    }

    private fun splitRaw(result: MutableList<NerToken>, raw: String, start: Int, bodyRelative: Boolean) {
        if (raw.isEmpty()) return
        fun add(text: String, from: Int, to: Int) {
            result += NerToken(text, from.takeIf { bodyRelative }, to.takeIf { bodyRelative })
        }
        if (raw.length > 1 && raw.first() in "(-") {
            add(raw.first().toString(), start, start + 1)
            splitRaw(result, raw.drop(1), start + 1, bodyRelative)
            return
        }
        if (raw.length > 1 && raw.last() in ")-") {
            splitRaw(result, raw.dropLast(1), start, bodyRelative)
            add(raw.last().toString(), start + raw.length - 1, start + raw.length)
            return
        }
        digitDotAlpha.matchEntire(raw)?.let {
            val left = it.groupValues[1]
            splitRaw(result, left, start, bodyRelative)
            add(".", start + left.length, start + left.length + 1)
            splitRaw(result, it.groupValues[2], start + left.length + 1, bodyRelative)
            return
        }
        sentenceGlue.matchEntire(raw)?.let {
            val left = it.groupValues[1]
            splitRaw(result, left, start, bodyRelative)
            add(".", start + left.length, start + left.length + 1)
            splitRaw(result, it.groupValues[2], start + left.length + 1, bodyRelative)
            return
        }
        splitPunctuation.firstOrNull { raw.indexOf(it) >= 0 }?.let { punctuation ->
            val index = raw.indexOf(punctuation)
            splitRaw(result, raw.substring(0, index), start, bodyRelative)
            add(punctuation.toString(), start + index, start + index + 1)
            splitRaw(result, raw.substring(index + 1), start + index + 1, bodyRelative)
            return
        }
        currencyAmount.matchEntire(raw)?.let {
            val left = it.groupValues[1]
            splitRaw(result, left, start, bodyRelative)
            splitRaw(result, it.groupValues[2], start + left.length, bodyRelative)
            return
        }
        if (raw.length > 1 && raw.last() in ".;:!,") {
            val core = raw.dropLast(1)
            if (core.length <= 2) {
                add(raw, start, start + raw.length)
            } else {
                splitRaw(result, core, start, bodyRelative)
                add(raw.last().toString(), start + core.length, start + raw.length)
            }
            return
        }
        add(raw, start, start + raw.length)
    }
}
