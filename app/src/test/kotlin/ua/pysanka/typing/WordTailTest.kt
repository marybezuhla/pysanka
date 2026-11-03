package ua.pysanka.typing

import org.junit.Assert.assertEquals
import org.junit.Test

class WordTailTest {
    @Test fun restOfTheWord() {
        assertEquals(3, WordTail.length("плі не зовсім"))
        assertEquals(3, WordTail.length("плі"))
    }

    @Test fun nothingAfterAWordEnd() {
        assertEquals(0, WordTail.length(" далі"))
        assertEquals(0, WordTail.length(", далі"))
        assertEquals(0, WordTail.length(""))
    }

    @Test fun apostropheAndHyphenInside() {
        assertEquals(4, WordTail.length("ʼясо смачне"))
        assertEquals(6, WordTail.length("о-небо."))
        // A dash after the word is not part of it
        assertEquals(2, WordTail.length("ло - так"))
    }

    @Test fun closingQuoteAndDecomposedLetters() {
        // 'he|llo': the closing quote stays
        assertEquals(3, WordTail.length("llo' said"))
        assertEquals(3, WordTail.length("llo’"))
        // «й» typed as «и» and a combining breve: the whole word
        assertEquals(4, WordTail.length("и\u0306ка є"))
    }
}
