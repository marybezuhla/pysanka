package ua.pysanka.typing

/**
 * Pysanka: swear words and slurs the word lists have (they come from film subtitles). They are not
 * taken out of the lists, so a person who types one is never "corrected" into another word; they
 * are only never offered (as a completion, a correction or a swipe) unless typed in full, while
 * "Block possibly offensive" is on (the default).
 */
object Offensive {
    /** Words that start like this (no ordinary word does). */
    private val STEMS = listOf(
        // Ukrainian and Russian
        "бляд", "блят", "хуй", "хуя", "хуе", "хуї", "пизд", "пізд", "ебан", "ебат", "ебал", "ёбан", "єбан",
        "уебок", "уёбок", "ублюд", "шлюх", "шльондр", "пидор", "пидар", "підор", "підар", "мудак", "мудил",
        "залуп", "гандон", "сучар", "курв", "жидов",
        // English
        "fuck", "cunt", "nigg", "whore", "faggot", "motherf",
    )

    /** Words that are offensive only as a whole («хер», not «Херсон»). */
    private val WORDS = setOf(
        "сука", "суки", "суку", "сукой", "суко", "сучка", "сучки", "сучку", "хер", "хера", "херня",
        "жид", "жиды", "жида", "жидів", "жиди", "негр", "негры", "негра", "негри", "чмо", "педик", "педики",
        "бля",
        "shit", "bitch", "bitches", "slut", "fag", "dick", "asshole",
    )

    fun isOffensive(word: String): Boolean {
        val w = word.lowercase()
        return w in WORDS || STEMS.any { w.startsWith(it) }
    }
}
