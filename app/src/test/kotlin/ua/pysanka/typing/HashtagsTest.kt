package ua.pysanka.typing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HashtagsTest {
    private val freq = mapOf("ранкова" to 120, "кава" to 160, "києві" to 90, "сьогодні" to 200)

    private fun suggest(prefix: String, text: String, own: PersonalWords? = null) =
        Hashtags.suggest(prefix, text, own, { freq[it.lowercase()] ?: 0 }, { p -> listOf("кавуни", "кавʼярня").filter { it.startsWith(p) } })

    @Test fun theTagBeingTyped() {
        assertEquals("", Hashtags.typed("Ранкова кава #"))
        assertEquals("кав", Hashtags.typed("Ранкова кава #кав"))
        assertNull(Hashtags.typed("Ранкова кава"))
        // After Enter it is a new line, not the hashtag any more
        assertNull(Hashtags.typed("Ранкова кава #кава\n"))
        // Not inside a word or a link
        assertNull(Hashtags.typed("site.com/#top"))
    }

    @Test fun wordsOfThePostRarerFirst() {
        val tags = suggest("", "Ранкова кава в Києві сьогодні")
        assertEquals("Києві", tags.first())
        assertTrue("кава" in tags)
    }

    @Test fun ownTagsFirstThenThePostThenTheList() {
        val own = PersonalWords().apply { learn("кавоман", insisted = true, midSentence = false, day = 1) }
        val tags = suggest("кав", "Ранкова кава", own)
        assertEquals(listOf("кавоман", "кава", "кавуни", "кавʼярня"), tags)
    }
}
