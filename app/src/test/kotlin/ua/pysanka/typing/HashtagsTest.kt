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

    private val words = mapOf(
        "клавіатура" to 130, "клавіатури" to 130, "клавіатурі" to 130, "налаштування" to 130, "налаштуваннях" to 130,
        "свайп" to 100, "кава" to 77, "ранок" to 87, "мені" to 250, "може" to 180, "точний" to 120,
    )
    private fun keywords(text: String) = Hashtags.keywords(text, { words[it.lowercase()] ?: 0 }, max = 4)

    @Test fun keyWordsInThePlainForm() {
        // Forms of one word count together and are shown in the plain form
        assertEquals("клавіатура", keywords("Розмір клавіатури. На клавіатурі є свайп.").first())
        assertEquals(listOf("налаштування"), keywords("Тиснути в налаштуваннях").filter { it.startsWith("налашт") })
    }

    @Test fun littleWordsTyposAndDescribingWordsLeftOut() {
        val tags = keywords("Мені може сподобатися ранок і кава, доттого точний")
        assertTrue("мені" !in tags && "може" !in tags)
        // A word the list does not know, once: maybe a typo
        assertTrue("доттого" !in tags)
        // A describing word comes after names of things
        assertTrue(tags.indexOf("точний") > tags.indexOf("кава"))
    }

    @Test fun namesKeepTheirCapitalTagsAlreadyThereAreLeftOut() {
        assertEquals("Писанка", keywords("Свайп у Писанка і знову Писанка").first())
        assertTrue("кава" !in keywords("Ранкова кава #кава"))
        // At a sentence's start a capital is not a name
        assertTrue("Кава" !in keywords("Кава зранку. Кава вдень."))
    }

    @Test fun aLanguageWithoutAWordList() {
        // Every word is unknown there: one used once counts, forms are left as written
        val tags = Hashtags.keywords("Rano kawa i spacer nad rzeką", { 0 }, max = 4, hasList = false)
        assertTrue("kawa" in tags && "spacer" in tags)
    }
}
