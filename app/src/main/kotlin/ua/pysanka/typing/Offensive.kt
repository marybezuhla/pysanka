package ua.pysanka.typing

/**
 * Pysanka: swear words and slurs the word lists have (they come from film subtitles). They are not
 * taken out of the lists, so a person who types one is never "corrected" into another word; they
 * are only never offered (as a completion, a correction or a swipe) unless typed in full, while
 * "Block possibly offensive" is on (the default). Each language has its own list (an ordinary word
 * of one language can be a swear word in another: Romanian «cu», German «Kurve», English «dick»);
 * Ukrainian, Russian and English here, the others in [LanguageExtras].
 */
object Offensive {
    private class Lists(val stems: List<String>, val words: Set<String>)

    /** Ukrainian, Russian and Belarusian: words that start like this (no ordinary word does). */
    private val CYRILLIC = Lists(
        listOf(
            "бляд", "блят", "хуй", "хуя", "хуе", "хуї", "пизд", "пізд", "ебан", "ебат", "ебал", "ёбан", "єбан",
            "уебок", "уёбок", "ублюд", "шлюх", "шльондр", "пидор", "пидар", "підор", "підар", "мудак", "мудил",
            "залуп", "гандон", "сучар", "курв", "жидов",
        ),
        setOf(
            "сука", "суки", "суку", "сукой", "суко", "сучка", "сучки", "сучку", "хер", "хера", "херня",
            "жид", "жиды", "жида", "жидів", "жиди", "негр", "негры", "негра", "негри", "чмо", "педик", "педики", "бля",
        ),
    )

    private val ENGLISH = Lists(
        listOf("fuck", "cunt", "nigg", "whore", "faggot", "motherf"),
        setOf("shit", "bitch", "bitches", "slut", "fag", "dick", "asshole"),
    )

    private fun builtIn(language: String?): Lists? = when (language) {
        "uk", "ru", "be" -> CYRILLIC
        "en" -> ENGLISH
        else -> null
    }

    /** Whether [word] is a swear word or a slur in [language] (null: any language Pysanka has a list of). */
    fun isOffensive(word: String, language: String? = null): Boolean {
        val w = word.lowercase()
        fun hit(stems: List<String>, words: Set<String>) = w in words || stems.any { w.startsWith(it) }
        if (language == null) {
            return hit(CYRILLIC.stems, CYRILLIC.words) || hit(ENGLISH.stems, ENGLISH.words) ||
                LanguageExtras.offensive.values.any { hit(it.stems, it.words) }
        }
        val own = builtIn(language)?.let { hit(it.stems, it.words) } == true
        return own || LanguageExtras.offensive[language]?.let { hit(it.stems, it.words) } == true
    }
}
