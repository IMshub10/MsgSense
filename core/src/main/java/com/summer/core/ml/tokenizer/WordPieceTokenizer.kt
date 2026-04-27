package com.summer.core.ml.tokenizer

import com.summer.core.ml.util.Constants.CLS_TOKEN
import com.summer.core.ml.util.Constants.PADDING_TOKEN
import com.summer.core.ml.util.Constants.SEPARATOR_TOKEN
import com.summer.core.ml.util.Constants.TOKEN_MAX_LENGTH
import com.summer.core.ml.util.Constants.UNKNOWN_TOKEN

class WordPieceTokenizer(private val vocab: Map<String, Long>) {
    private val unknownTokenId: Long = resolveTokenId(UNKNOWN_TOKEN, "<unk>")
    private val paddingTokenId: Long = resolveTokenId(PADDING_TOKEN, "<pad>")

    private val regex = Regex(
        "(?<=\\p{L})(?=[^\\p{L}\\d])" +      // Split letters & non-letters
                "|(?<=[^\\p{L}\\d])(?=\\p{L})" +    // Split non-letters & letters
                "|(?<=\\d)(?=[^\\d\\p{L}])" +       // Keep 20th, 1st together
                "|(?<=[^\\d\\p{L}])(?=\\d)" +       // Split before numbers
                "|(?<=\\p{P})(?=\\p{P})" +      // Split consecutive punctuation
                "|(?<=\\p{N})(?=\\+)" +             // Keep "+919876543210" together
                "|(?<=\\+)(?=\\p{N})" +             // Handle "+91" correctly
                "|(?<=<)(?=\\p{N})" +               // Keep encoded sequences inside "<...>"
                "|(?<=\\p{N})(?=>)" +               // Keep hex sequences inside "<...>"
                "|(?<=\")|(?=\")" +                 // **Ensures `"` is properly separated**
                "|\\s+"
    )

    private fun convertTextToListOfWords(text: String): List<String> {
        return text
            .lowercase()
            .split(regex)
            .map(String::trim)
            .filter(String::isNotBlank)
            .toList()
    }

    /**
     * WordPiece-style tokenization
     */
    @Throws(IllegalStateException::class)
    fun tokenize(text: String, maxLength: Int = TOKEN_MAX_LENGTH): LongArray {
        val tokens = mutableListOf<Long>()

        // Add [CLS] token at the beginning
        tokens.add(vocab[CLS_TOKEN] ?: error("Missing $CLS_TOKEN token"))

        // Use regex to split text while preserving punctuation
        val words = convertTextToListOfWords(text)

        for (word in words) {
            if (vocab.containsKey(word)) {
                tokens.add(vocab[word]!!)
                continue
            }

            var start = 0

            while (start < word.length) {
                var found = false
                for (end in word.length downTo (start + 1)) {
                    val candidate = if (start == 0) word.substring(start, end)
                    else "##" + word.substring(start, end)

                    if (vocab.containsKey(candidate)) {
                        tokens.add(vocab[candidate]!!)
                        start = end
                        found = true
                        break
                    }
                }
                if (!found) {
                    // No sub word found, mark as unknown and move forward to avoid infinite loop
                    tokens.add(unknownTokenId)
                    break
                }
            }
        }

        // Add [SEP] token at the end
        tokens.add(vocab[SEPARATOR_TOKEN] ?: error("Missing $SEPARATOR_TOKEN token"))

        // Add padding at the end if word tokens less that maxLength
        val paddedTokens = if (tokens.size < maxLength) {
            tokens + List(maxLength - tokens.size) { paddingTokenId }
        } else {
            tokens.take(maxLength) // Trim tokens to max length
        }

        return paddedTokens.toLongArray()
    }

    private fun resolveTokenId(primary: String, fallback: String): Long {
        return vocab[primary] ?: vocab[fallback]
        ?: error("Missing required tokenizer token: $primary or $fallback")
    }
}
