package ua.pysanka.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import ua.pysanka.ai.TextParts.Part

class TextPartsTest {
    private fun part(text: String, cursor: Int, p: Part) = TextParts.around(text, cursor)[p]?.let { text.substring(it.first, it.last + 1) }

    private val text = "Привіт! Як справи? Я вже вдома.\n\nЗавтра зустрінемось о десятій. Не запізнюйся."

    @Test fun sentenceAroundTheCursor() {
        assertEquals("Як справи?", part(text, text.indexOf("справи"), Part.SENTENCE))
        assertEquals("Привіт!", part(text, 3, Part.SENTENCE))
        assertEquals("Не запізнюйся.", part(text, text.length, Part.SENTENCE))
    }

    @Test fun rightAfterAFullStopIsThatSentence() {
        val at = text.indexOf("справи?") + "справи?".length
        assertEquals("Як справи?", part(text, at, Part.SENTENCE))
    }

    @Test fun paragraphAroundTheCursor() {
        assertEquals("Завтра зустрінемось о десятій. Не запізнюйся.", part(text, text.indexOf("десятій"), Part.PARAGRAPH))
        assertEquals("Привіт! Як справи? Я вже вдома.", part(text, 2, Part.PARAGRAPH))
    }

    @Test fun samePartsAreLeftOut() {
        val one = "Лише одне речення без крапки"
        val parts = TextParts.around(one, 5)
        assertFalse(Part.PARAGRAPH in parts)
        assertFalse(Part.SENTENCE in parts)
        assertEquals(one, part(one, 5, Part.ALL))
    }

    @Test fun nothingForEmptyOrUnknownCursor() {
        assertEquals(emptyMap<Part, IntRange>(), TextParts.around("   ", 1))
        assertEquals(emptyMap<Part, IntRange>(), TextParts.around("Текст", -1))
    }
}
