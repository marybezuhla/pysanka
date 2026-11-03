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
 * 2. words of the text being written, the rarer first («Ранкова кава в Києві #» → #кава, #Києві);
 * 3. once a few letters are typed, words of the word list that begin with them.
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
        // 2. Words of the text: longer and rarer ones first (those say what the post is about)
        val words = WORD.findAll(text).map { it.value }.filter { it.length >= 4 && PersonalWords.key(it).startsWith(p) }.distinct().toList()
        words.sortedWith(compareBy({ frequency(it).let { f -> if (f == 0) 0 else f } }, { -it.length })).take(max).forEach { add(it) }
        // 3. The word list
        if (p.length >= 2) complete(prefix).forEach { add(it) }
        return out.values.toList()
    }

    /** A hashtag the person finished (with a space or a tap) is remembered, unless the field is private. */
    fun learn(context: Context, tag: String) {
        if (ua.pysanka.privacy.FieldPrivacy.isPrivate || ua.pysanka.privacy.FieldPrivacy.isPassword) return
        val prefs by dev.patrickgold.florisboard.app.FlorisPreferenceStore
        if (!prefs.correction.pysankaLearnWords.get() || !prefs.suggestion.pysankaHashtags.get()) return
        if (PersonalWords.isLearnable(tag)) UserWords.learn(context, LIST, tag, insisted = true, midSentence = false, previous = null)
    }
}

/** Pysanka: a hashtag offered after «#»; tapping it puts «#tag » in place of what was typed. */
data class HashtagCandidate(val tag: String) : SuggestionCandidate {
    override val text: CharSequence = "#$tag"
    override val secondaryText: CharSequence? = null
    override val confidence: Double = 0.5
    override val isEligibleForAutoCommit: Boolean = false
    override val isEligibleForUserRemoval: Boolean = false
    override val icon: ImageVector? = null
    override val sourceProvider: SuggestionProvider? = null
}
