package com.summer.notifai.banking_ner

/**
 * Stage 4 of the banking NER pipeline: collapse subword argmax to word-level BIO labels,
 * then group into named entities.
 *
 * Runtime-agnostic. Shared between the ONNX and TFLite pipelines.
 */
object BankingBioDecoder {

    val LABEL_LIST = listOf(
        "O",
        "B-ACCOUNT", "B-AMOUNT", "B-BALANCE", "B-BANK", "B-CARD_TYPE",
        "B-DATE", "B-DIRECTION", "B-LIMIT", "B-MERCHANT", "B-REF_ID",
        "B-TXN_TYPE", "B-UPI_ID",
        "I-ACCOUNT", "I-AMOUNT", "I-BALANCE", "I-BANK", "I-CARD_TYPE",
        "I-DATE", "I-DIRECTION", "I-LIMIT", "I-MERCHANT", "I-REF_ID",
        "I-TXN_TYPE", "I-UPI_ID",
    )

    /**
     * Collapse 128-length argmax to one label per word using word_ids.
     * Only the **first subword** of each word contributes its label;
     * continuation subwords are ignored.
     *
     * @param argmax   per-position class index (len 128)
     * @param wordIds  subword-to-word mapping; -1 = special/padding (len 128)
     * @return one label string per word (same length as the Stage 1 word list)
     */
    fun collapseToWordLabels(
        argmax: List<Int>,
        wordIds: List<Int>,
    ): List<String> {
        val labels = mutableListOf<String>()
        var prevWordId = -1

        for (i in argmax.indices) {
            val wid = wordIds[i]
            if (wid == -1) {
                prevWordId = -1
                continue
            }
            if (wid != prevWordId) {
                labels.add(LABEL_LIST[argmax[i]])
            }
            prevWordId = wid
        }
        return labels
    }

    /**
     * Walk `(word, label)` pairs and group into named entities using BIO scheme.
     *
     * - `B-XXX` starts a new entity of type XXX
     * - `I-XXX` continues the current entity **only if** the current type is also XXX
     * - `O` or type mismatch closes any open entity
     *
     * @return map of entity type → list of entity texts (space-joined tokens)
     */
    fun groupEntities(
        words: List<String>,
        wordLabels: List<String>,
    ): Map<String, List<String>> {
        val entities = mutableMapOf<String, MutableList<String>>()
        var currentType: String? = null
        val currentTokens = mutableListOf<String>()

        fun flush() {
            if (currentType != null && currentTokens.isNotEmpty()) {
                entities.getOrPut(currentType!!) { mutableListOf() }
                    .add(currentTokens.joinToString(" "))
            }
            currentType = null
            currentTokens.clear()
        }

        for ((word, label) in words.zip(wordLabels)) {
            when {
                label.startsWith("B-") -> {
                    flush()
                    currentType = label.removePrefix("B-")
                    currentTokens.add(word)
                }
                label.startsWith("I-") -> {
                    val iType = label.removePrefix("I-")
                    if (iType == currentType) {
                        currentTokens.add(word)
                    } else {
                        flush()
                    }
                }
                else -> {
                    flush()
                }
            }
        }
        flush()

        return entities
    }
}
