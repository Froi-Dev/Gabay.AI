package com.example.gabayai.semantic

import java.text.Normalizer
import java.util.Locale

/** BERT uncased basic tokenization and greedy WordPiece, using the publisher's unmodified vocabulary. */
internal class BertWordPieceTokenizer(vocabulary: List<String>, private val maximumTokens: Int = 64) {
    private val vocabulary = vocabulary.withIndex().associate { it.value to it.index.toLong() }

    init {
        require(maximumTokens in 3..256)
        require(this.vocabulary["[PAD]"] == 0L && this.vocabulary["[UNK]"] == 100L)
        require(this.vocabulary["[CLS]"] == 101L && this.vocabulary["[SEP]"] == 102L)
    }

    fun encode(text: String): LongArray {
        val result = ArrayList<Long>(maximumTokens)
        result += 101L
        for (token in basicTokens(text)) {
            val pieces = wordPieces(token)
            // Reject instead of silently truncating a long OCR line into a different field label.
            require(result.size + pieces.size < maximumTokens) { "Field label exceeds semantic token limit" }
            result += pieces
        }
        result += 102L
        return result.toLongArray()
    }

    private fun basicTokens(text: String): List<String> {
        val cleaned = StringBuilder()
        text.codePoints().forEach { codePoint ->
            when {
                codePoint == 0 || codePoint == 0xfffd -> Unit
                isWhitespace(codePoint) -> cleaned.append(' ')
                Character.getType(codePoint) in setOf(Character.CONTROL.toInt(), Character.FORMAT.toInt()) -> Unit
                isChinese(codePoint) -> cleaned.append(' ').appendCodePoint(codePoint).append(' ')
                else -> cleaned.appendCodePoint(codePoint)
            }
        }
        val normalized = Normalizer.normalize(cleaned.toString().lowercase(Locale.ROOT), Normalizer.Form.NFD)
        val tokens = ArrayList<String>()
        val current = StringBuilder()
        fun flush() {
            if (current.isNotEmpty()) {
                tokens += current.toString()
                current.setLength(0)
            }
        }
        normalized.codePoints().forEach { codePoint ->
            when {
                Character.getType(codePoint) == Character.NON_SPACING_MARK.toInt() -> Unit
                isWhitespace(codePoint) -> flush()
                isPunctuation(codePoint) -> { flush(); tokens += String(Character.toChars(codePoint)) }
                else -> current.appendCodePoint(codePoint)
            }
        }
        flush()
        return tokens
    }

    private fun wordPieces(token: String): List<Long> {
        if (token.codePointCount(0, token.length) > 100) return listOf(100L)
        val pieces = ArrayList<Long>()
        var start = 0
        while (start < token.length) {
            var end = token.length
            var found: Long? = null
            while (end > start) {
                val piece = (if (start == 0) "" else "##") + token.substring(start, end)
                found = vocabulary[piece]
                if (found != null) break
                end = token.offsetByCodePoints(end, -1)
            }
            if (found == null) return listOf(100L)
            pieces += found
            start = end
        }
        return pieces
    }

    private fun isWhitespace(codePoint: Int) = codePoint in setOf(9, 10, 13, 32) || Character.getType(codePoint) == Character.SPACE_SEPARATOR.toInt()

    private fun isPunctuation(codePoint: Int): Boolean = codePoint in 33..47 || codePoint in 58..64 ||
        codePoint in 91..96 || codePoint in 123..126 || Character.getType(codePoint) in setOf(
            Character.CONNECTOR_PUNCTUATION.toInt(), Character.DASH_PUNCTUATION.toInt(),
            Character.START_PUNCTUATION.toInt(), Character.END_PUNCTUATION.toInt(),
            Character.INITIAL_QUOTE_PUNCTUATION.toInt(), Character.FINAL_QUOTE_PUNCTUATION.toInt(),
            Character.OTHER_PUNCTUATION.toInt(),
        )

    private fun isChinese(codePoint: Int) = codePoint in 0x4e00..0x9fff || codePoint in 0x3400..0x4dbf ||
        codePoint in 0x20000..0x2a6df || codePoint in 0x2a700..0x2b73f || codePoint in 0x2b740..0x2b81f ||
        codePoint in 0x2b820..0x2ceaf || codePoint in 0xf900..0xfaff || codePoint in 0x2f800..0x2fa1f
}
