package com.example.gabayai.semantic

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class BertWordPieceTokenizerTest {
    private fun tokenizer(maximumTokens: Int = 64): BertWordPieceTokenizer {
        val relative = "src/semantic/assets/models/minilm/vocab.txt"
        val vocabulary = sequenceOf(File(relative), File("app/$relative")).first { it.isFile }.readLines()
        return BertWordPieceTokenizer(vocabulary, maximumTokens)
    }

    @Test fun originalVocabularyProducesExpectedBertIds() {
        assertArrayEquals(longArrayOf(101, 10690, 3642, 1024, 102), tokenizer().encode("Postal Code:"))
        assertArrayEquals(longArrayOf(101, 3058, 1997, 4182, 102), tokenizer().encode("DATE OF BIRTH"))
    }

    @Test fun uncasedNormalizationStripsAccentsAndControls() {
        assertArrayEquals(longArrayOf(101, 15743, 7668, 102), tokenizer().encode("NAÏVE café"))
        assertArrayEquals(longArrayOf(101, 10690, 3642, 102), tokenizer().encode("po\u0000stal\tcode"))
    }

    @Test fun greedyWordPieceUsesContinuationVocabulary() {
        assertArrayEquals(longArrayOf(101, 29379, 8237, 102), tokenizer().encode("FooBar"))
    }

    @Test fun unknownTokenUsesPublisherUnknownId() {
        assertArrayEquals(longArrayOf(101, 100, 102), tokenizer().encode("😀"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun longLabelsAreRejectedInsteadOfSilentlyTruncated() {
        tokenizer(maximumTokens = 5).encode("date of birth place")
    }
}
