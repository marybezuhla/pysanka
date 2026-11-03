package ua.pysanka.typing

import android.content.Context
import androidx.compose.ui.graphics.vector.ImageVector
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.SuggestionProvider

/**
 * Pysanka: hashtags without AI. After «#» the suggestions are hashtags:
 *
 * 1. the person's own, used before (kept like learned words: encrypted, never from a password,
 *    incognito or "no learning" field);
 * 2. the text's key words ([keywords]): rarer words that come up more, little words and likely
 *    typos left out, each word once in its plain form («клавіатури», «клавіатурі» → #клавіатура);
 * 3. once a few letters are typed, words of the word list that begin with them.
 *
 * Right after «#» the first suggestion is a few key words at once (one tap puts them all).
 *
 * Turned on and off in the settings, like emoji for words.
 */
object Hashtags {
    /** The learned hashtags' own list (see [UserWords]); not a language. */
    const val LIST = "tags"

    private val TYPED = Regex("""(?:^|[\s(«"])#([\p{L}\p{N}_]*)\z""")
    /** A word that can be a hashtag: letters, digits and «_» only (no apostrophe or hyphen). */
    private val WORD = Regex("""(?<![\p{L}\p{N}_'ʼ’-])[\p{L}][\p{L}\p{N}_]*(?![\p{L}\p{N}_'ʼ’-])""")

    /** The hashtag being typed right before the cursor (without «#»; empty just after «#»), or null. */
    fun typed(textBefore: String): String? {
        val tail = textBefore.takeLast(64)
        val m = TYPED.find(tail) ?: return null
        // «#» at the very start of a cut tail: only if what comes before it is a boundary too
        if (m.range.first == 0 && tail[0] == '#' && textBefore.length > tail.length) {
            val before = textBefore[textBefore.length - tail.length - 1]
            if (!before.isWhitespace() && before !in "(«\"") return null
        }
        return m.groupValues[1]
    }

    /**
     * Up to [max] hashtags (without «#») for [prefix], from [own] ones, the [text] being written and
     * the word list ([frequency]: 0 for a word it does not know, [complete]: its words beginning
     * with a prefix).
     */
    fun suggest(
        prefix: String, text: String, own: PersonalWords?, frequency: (String) -> Int, complete: (String) -> List<String>, max: Int = 5,
    ): List<String> {
        val p = PersonalWords.key(prefix)
        val out = LinkedHashMap<String, String>()
        fun add(tag: String) {
            if (out.size < max && tag.length >= 2 && PersonalWords.key(tag) != p) out.putIfAbsent(PersonalWords.key(tag), tag)
        }
        // 1. The person's own
        own?.let { o ->
            val keys = if (p.isEmpty()) o.known().sortedByDescending { o.count(it) } else o.withPrefix(p, max)
            keys.take(max).forEach { add(o.display(it) ?: it) }
        }
        // 2. The text's key words
        keywords(text, frequency, complete, max = 12).filter { PersonalWords.key(it).startsWith(p) }.forEach { add(it) }
        // 3. The word list
        if (p.length >= 2) complete(prefix).forEach { add(it) }
        return out.values.toList()
    }

    /** How many key words go into the all-at-once suggestion. */
    const val BUNDLE = 4

    /**
     * Endings of other cases and of the plural, each with the endings of the plain form to try in
     * its place, the likelier first («клавіатури» → «клавіатура», «налаштуваннях» → «налаштування»,
     * «простору» → «простір» is not found, «простор» neither, so it stays).
     */
    private val OBLIQUE = listOf(
        "ами" to listOf("а", "я", "и", ""), "ями" to listOf("я", "а", "і", ""), "ові" to listOf("", "ь"),
        "еві" to listOf("ь", ""), "ах" to listOf("а", "и", ""), "ях" to listOf("я", "і", ""),
        "ою" to listOf("а"), "ею" to listOf("я", "а"), "ів" to listOf("", "и"), "ам" to listOf("а", "и", ""),
        "ям" to listOf("я", "і"), "и" to listOf("а", "", "я"), "і" to listOf("а", "я", "о", "ь", ""), "у" to listOf("", "а"),
        "ю" to listOf("я", "ь"),
    )

    /** Endings of describing words and verbs: they say less about a post than names of things. */
    private val WEAK = listOf(
        "ний", "ній", "ні", "ного", "ному", "ною", "них", "ним", "ними", "ська", "ське", "ські", "ський", "ова",
        "ове", "овий", "ий", "ти", "тися", "ться", "ють", "ують", "ять", "ала", "ало", "али", "или", "іли",
        "ила", "ило", "ував", "ює", "ує", "ає", "ись", "ися", "им", "ім", "лю", "аю", "ую", "яю", "ію", "ить", "ать", "ять", "еть", "уть",
    )

    /** Whether [key] looks like a describing word or a verb (see [WEAK]; «звикли», not «школи»). */
    private fun weak(key: String): Boolean =
        WEAK.any { key.length > it.length + 2 && key.endsWith(it) } ||
            (key.length > 4 && key.endsWith("ли") && key[key.length - 3] !in "ое")

    /** Words that are never a hashtag by themselves (pronouns, little words, the most common verbs). */
    private val STOP = setOf(
        "мене", "мені", "тебе", "тобі", "його", "йому", "вона", "вони", "нами", "вами", "ними", "собі", "себе",
        "який", "яка", "яке", "які", "якого", "якій", "яких", "цей", "цього", "цієї", "цих", "того", "тієї",
        "тих", "також", "тому", "чому", "коли", "тоді", "тепер", "зараз", "дуже", "може", "можна", "треба",
        "буде", "було", "була", "були", "бути", "мати", "має", "маю", "немає", "щоб", "якщо", "тільки", "лише",
        "вже", "навіть", "після", "перед", "через", "біля", "серед", "щодо", "просто", "саме", "хочу", "хоче",
        "знаю", "кажу", "думаю", "весь", "вся", "все", "всі", "усі", "усе", "інше", "інший", "інша", "інші",
        "багато", "мало", "більше", "менше", "найбільше", "завжди", "ніколи", "іноді", "сьогодні", "завтра",
        "вчора", "туди", "сюди", "звідки", "куди", "проте", "однак", "наче", "ніби", "отже", "варто", "можу",
        "буду", "будеш", "будемо", "одне", "один", "одна", "одні", "таких", "такий", "така", "таке", "такі",
        "наскільки", "настільки", "скільки", "стільки", "подивись", "подивіться", "дивись", "будь", "ласка",
        "дякую", "привіт", "добре", "звісно", "напевно", "мабуть", "взагалі", "особливо", "майже",
        "this", "that", "with", "from", "have", "your", "about", "would", "could", "should", "there", "their",
        "they", "them", "what", "when", "where", "which", "will", "just", "more", "very", "some", "into", "than",
    )

    private fun stem(key: String) = key.take(maxOf(4, minOf(key.length - 2, 7)))

    /** The plain form of [key] (lower case) the word list knows, or [key] itself. */
    private fun plain(key: String, frequency: (String) -> Int): String {
        for ((ending, tries) in OBLIQUE) {
            if (key.length <= ending.length + 2 || !key.endsWith(ending)) continue
            val base = key.dropLast(ending.length)
            // The commonest of the forms tried (the plain form usually is), the likelier first among equals
            tries.map { base + it }.filter { it.length >= 4 && frequency(it) > 0 }
                .maxByOrNull { frequency(it) }?.let { return it }
            return key
        }
        return key
    }

    /**
     * Pysanka: what [text] is about, without AI: its words of at least 4 letters but the little
     * ones, each word once (its forms together, shown in the plain form the word list knows:
     * «клавіатури» → «клавіатура»); the rarer ([frequency]: 0 for a word the list does not know),
     * the more often used and naming things rather than describing them, the higher. A word the
     * list does not know only if used twice (once it may be a typo). A capital is kept only for a
     * name (not at a sentence's start). Hashtags the text already has are left out.
     */
    @Suppress("UNUSED_PARAMETER")
    fun keywords(text: String, frequency: (String) -> Int, complete: (String) -> List<String> = { emptyList() }, max: Int = 5): List<String> {
        val tagged = Regex("""#([\p{L}\p{N}_]+)""").findAll(text).map { PersonalWords.key(it.groupValues[1]) }.toSet()
        class Group(val forms: MutableList<String> = mutableListOf(), var count: Int = 0, var first: Int = 0, var name: Boolean = false)
        val groups = LinkedHashMap<String, Group>()
        for ((i, m) in WORD.findAll(text).withIndex()) {
            val w = m.value
            val key = PersonalWords.key(w)
            if (key.length < 4 || key in STOP || key.any { it.isDigit() } || key in tagged) continue
            val g = groups.getOrPut(stem(key)) { Group(first = i) }
            g.forms += w
            g.count++
            // A capital not at a sentence's start: a name
            val before = text.substring(0, m.range.first).trimEnd()
            if (w.first().isUpperCase() && before.isNotEmpty() && before.last() !in ".!?…\n") g.name = true
        }
        val scored = groups.mapNotNull { (_, g) ->
            val known = g.forms.maxOf(frequency)
            if (known == 0 && g.count < 2) return@mapNotNull null
            val weak = g.forms.count { weak(PersonalWords.key(it)) } * 2 > g.forms.size
            val best = if (g.name) g.forms.first() else if (weak) PersonalWords.key(g.forms.first()) else {
                g.forms.map { plain(PersonalWords.key(it), frequency) }.maxWith(compareBy({ frequency(it) }, { -it.length }))
            }
            val rarity = if (known == 0) 0.6 else (256 - known) / 256.0
            val score = g.count * rarity * (if (weak) 0.4 else 1.0) + best.length / 40.0
            Triple(best, score, g.first)
        }
        return scored.sortedWith(compareByDescending<Triple<String, Double, Int>> { it.second }.thenBy { it.third })
            .map { it.first }.distinctBy { PersonalWords.key(it) }.take(max)
    }

    /** A hashtag the person finished (with a space or a tap) is remembered, unless the field is private. */
    fun learn(context: Context, tag: String) {
        if (ua.pysanka.privacy.FieldPrivacy.isPrivate || ua.pysanka.privacy.FieldPrivacy.isPassword) return
        val prefs by dev.patrickgold.florisboard.app.FlorisPreferenceStore
        if (!prefs.correction.pysankaLearnWords.get() || !prefs.suggestion.pysankaHashtags.get()) return
        if (PersonalWords.isLearnable(tag)) UserWords.learn(context, LIST, tag, insisted = true, midSentence = false, previous = null)
    }
}

/**
 * Pysanka: a hashtag offered after «#», or a few at once ([tags]); tapping it puts «#tag » (or
 * «#a #b #c ») in place of what was typed.
 */
data class HashtagCandidate(val tags: List<String>) : SuggestionCandidate {
    constructor(tag: String) : this(listOf(tag))

    override val text: CharSequence = tags.joinToString(" ") { "#$it" }
    override val secondaryText: CharSequence? = null
    override val confidence: Double = 0.5
    override val isEligibleForAutoCommit: Boolean = false
    override val isEligibleForUserRemoval: Boolean = false
    override val icon: ImageVector? = null
    override val sourceProvider: SuggestionProvider? = null
}
