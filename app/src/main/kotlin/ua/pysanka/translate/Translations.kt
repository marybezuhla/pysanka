package ua.pysanka.translate

import android.content.Context
import java.lang.ref.SoftReference
import java.text.Normalizer
import java.util.Locale

/**
 * Pysanka: translation of words between the twelve languages, offline, from Wiktionary (made by
 * scripts/translate/build.py; the files are described there). Nothing is learned or kept: a lookup
 * reads the dictionaries in the app and returns.
 *
 * A word is found as written or by its dictionary form («ключа» → «ключ», «klucza» → «klucz»).
 * The translations are the words that share a meaning with it in the other language, the ones of
 * more meanings first; each comes with a hint of what meaning it is for: its words in the person's
 * own language («джерело» for "spring"), or in English when translating into that language.
 */
object Translations {
    const val DIR = "ime/translate"

    /** The languages, in the order they are offered. */
    val LANGUAGES = listOf("uk", "en", "pl", "de", "fr", "es", "pt", "ro", "hu", "sk", "be", "ru")

    /** How many translations are shown at most. */
    const val MAX_RESULTS = 16

    /** How many of a word's first meanings give their commonest word even if no other meaning agrees. */
    private const val LEAD_MEANINGS = 8

    /** Longer text is not a word or an expression: not looked up. */
    const val MAX_CHARS = 60

    data class Result(
        /** The translation, as written in the dictionary. */
        val word: String,
        /** What meaning it is for (other words of it, in the person's language or English). */
        val hint: String?,
        /** Found only through English (no meaning joins the two languages directly). */
        val viaEnglish: Boolean = false,
    )

    data class Lookup(
        val from: String,
        val to: String,
        /** The dictionary forms found for the text («ключ» for «ключа»); empty if none. */
        val lemmas: List<String>,
        val results: List<Result>,
    )

    /** Where the files are read from: the app's assets (the tests read them from the sources). */
    fun interface Source {
        fun read(name: String): String?
    }

    fun source(context: Context): Source = Source { name ->
        runCatching { context.assets.open("$DIR/$name").bufferedReader(Charsets.UTF_8).use { it.readText() } }.getOrNull()
    }

    private val cache = HashMap<String, SoftReference<SortedLines>>()

    /** A file of the dictionary, read once and kept while memory allows. */
    @Synchronized
    internal fun file(src: Source, name: String): SortedLines? {
        cache[name]?.get()?.let { return it }
        val text = src.read(name) ?: return null
        return SortedLines(text).also { cache[name] = SoftReference(it) }
    }

    /** Whether the dictionaries are in this build. */
    fun available(context: Context): Boolean =
        runCatching { context.assets.list(DIR)?.contains("uk.words.txt") == true }.getOrDefault(false)

    /** Stress marks on Cyrillic letters (on Latin ones the same marks are letters: é, á, ó). */
    private val STRESS = Regex("(?<=[\\u0400-\\u04FF])[\\u0301\\u0300]")
    private val SPACES = Regex("\\s+")

    /** The text as the dictionary keeps it: small letters, no stress marks, one kind of apostrophe. */
    fun key(text: String): String {
        val t = text.trim().trim { !it.isLetterOrDigit() && it != '\'' && it != '’' && it != 'ʼ' }
            .replace('’', '\'').replace('ʼ', '\'').replace('`', '\'').replace('ʹ', '\'')
        val bare = Normalizer.normalize(STRESS.replace(Normalizer.normalize(t, Normalizer.Form.NFD), ""), Normalizer.Form.NFC)
        return SPACES.replace(bare, " ").lowercase(Locale.ROOT)
    }

    private fun startsCyrillic(text: String): Boolean = text.firstOrNull { it.isLetter() }?.let { it in '\u0400'..'\u04FF' } == true

    private fun cyrillic(lang: String) = lang == "uk" || lang == "ru" || lang == "be"

    /** The dictionary forms of [k] in [lang]: itself if it is a word, and what it is a form of. */
    fun lemmas(src: Source, lang: String, k: String, endings: Boolean = true): List<String> {
        val words = file(src, "$lang.words.txt") ?: return emptyList()
        val out = LinkedHashSet<String>()
        if (words.find(k) != null) out += k
        file(src, "$lang.forms.txt")?.find(k)?.split('|')?.forEach { if (it.isNotEmpty()) out += it }
        if (out.isEmpty() && (lang == "ru" || lang == "be") && 'ё' in k) {
            return lemmas(src, lang, k.replace('ё', 'е'), endings)
        }
        if (lang == "en" && endings) {
            // English endings ("keys", "opened", "running"): the dictionary has few forms of its own,
            // and "keys" as a word of its own has fewer translations than "key"
            englishStems(k).firstOrNull { words.find(it) != null }?.let { out += it }
        }
        return out.toList()
    }

    internal fun englishStems(k: String): List<String> {
        val out = mutableListOf<String>()
        fun drop(end: String, add: String = "") {
            if (k.length > end.length + 2 && k.endsWith(end)) out += k.dropLast(end.length) + add
        }
        drop("ies", "y"); drop("es"); drop("s")
        drop("ied", "y"); drop("ed"); drop("ed", "e")
        drop("ing"); drop("ing", "e")
        // "running" → "run", "stopped" → "stop"
        for (end in listOf("ing", "ed")) {
            val stem = k.removeSuffix(end)
            if (stem != k && stem.length > 2 && stem.last() == stem[stem.length - 2]) out += stem.dropLast(1)
        }
        return out
    }

    /**
     * The language of [text]: the first of [preferred] (the keyboard's languages), then of all, in
     * whose dictionary it is and whose letters it has. Null if no dictionary has it.
     */
    fun detect(src: Source, text: String, preferred: List<String>): String? {
        val k = key(text)
        if (k.isEmpty()) return null
        val isCyrillic = startsCyrillic(k)
        val order = (preferred + LANGUAGES).distinct().filter { it in LANGUAGES && cyrillic(it) == isCyrillic }
        // Letters only one language has decide by themselves («ї», «ў», «ъ»); «ы» is Russian or
        // Belarusian, «і» Ukrainian or Belarusian
        val only = when {
            'ў' in k -> "be"
            k.any { it in "їєґ" } -> "uk"
            'ъ' in k -> "ru"
            k.any { it in "ыэё" } -> if ('і' in k) "be" else null
            else -> null
        }
        if (only != null && lemmas(src, only, k).isNotEmpty()) return only
        val narrowed = when {
            k.any { it in "ыэё" } -> order.filter { it == "ru" || it == "be" }
            'і' in k -> order.filter { it == "uk" || it == "be" }
            isCyrillic -> order.filter { it == "uk" || it == "ru" }
            else -> order
        }
        // Words as they are first («Hund» is German, not "hun" + d)
        narrowed.firstOrNull { lemmas(src, it, k, endings = false).isNotEmpty() }?.let { return it }
        return order.firstOrNull { lemmas(src, it, k).isNotEmpty() }
    }

    /**
     * Translations of [text] from [from] to [to]. [hintLanguage]: the language of the hints (the
     * person's own), when the dictionary has words of the meaning in it.
     */
    fun translate(src: Source, text: String, from: String, to: String, hintLanguage: String): Lookup {
        val k = key(text)
        val lemmas = if (k.isEmpty() || k.length > MAX_CHARS) emptyList() else lemmas(src, from, k)
        if (lemmas.isEmpty() || from == to) return Lookup(from, to, lemmas, emptyList())
        // Read again if memory was short since lemmas(); nothing found if it cannot be
        val words = file(src, "$from.words.txt") ?: return Lookup(from, to, lemmas, emptyList())
        val ids = lemmas.flatMap { words.find(it)?.split(',').orEmpty() }.filter { it.isNotEmpty() }.distinct()
        val direct = collect(src, ids, from, to, hintLanguage, lemmas.toSet())
        if (direct.isNotEmpty() || from == "en" || to == "en") return Lookup(from, to, lemmas, direct)
        // Nothing joins the two directly: through the English words of its meanings
        val en = file(src, "en.meanings.txt")
        val enWords = file(src, "en.words.txt")
        val english = ids.asSequence().mapNotNull { en?.find(it) }.flatMap { it.split('|').take(2) }.distinct().take(4).toList()
        val via = english.flatMap { w -> enWords?.find(key(w))?.split(',').orEmpty().take(6) }.filter { it.isNotEmpty() }.distinct()
        val results = collect(src, via, "en", to, hintLanguage, english.map { key(it) }.toSet()).map { it.copy(viaEnglish = true) }
        return Lookup(from, to, lemmas, results.take(MAX_RESULTS / 2))
    }

    private class Found(val word: String, val first: Int, var support: Int, val hint: String?, var lead: Boolean)

    private fun collect(
        src: Source,
        ids: List<String>,
        from: String,
        to: String,
        hintLanguage: String,
        exclude: Set<String>,
    ): List<Result> {
        val target = file(src, "$to.meanings.txt") ?: return emptyList()
        // Hints in the person's language; else (translating into it) in English
        val hints = file(src, "${if (hintLanguage != to) hintLanguage else if (to != "en") "en" else from}.meanings.txt")
        val found = LinkedHashMap<String, Found>()
        // Ids are in the order of the meanings: the most basic first
        for ((n, id) in ids.sorted().withIndex()) {
            val ws = target.find(id)?.split('|') ?: continue
            for ((i, w) in ws.withIndex()) {
                val wk = key(w)
                if (wk.isEmpty() || (from == to && wk in exclude)) continue
                // The commonest word of one of the first (most widely shared) meanings
                val lead = i == 0 && n < LEAD_MEANINGS
                val f = found[wk]
                if (f != null) {
                    f.support++
                    f.lead = f.lead || lead
                } else {
                    found[wk] = Found(w, n, 1, hint(id, hints, exclude + wk), lead)
                }
            }
        }
        // When meanings agree on some words, a word of only one meaning is shown only if it is that
        // meaning's commonest (the «@» called «собачка» brings no «вухо»; «клавіша» for "key" stays)
        val best = found.values.maxOfOrNull { it.support } ?: 0
        val enough = if (best >= 3) 2 else 1
        return found.values.filter { it.support >= enough || it.lead }
            .sortedWith(compareByDescending<Found> { it.support }.thenBy { it.first })
            .take(MAX_RESULTS).map { Result(it.word, it.hint) }
    }

    private fun hint(id: String, hints: SortedLines?, exclude: Set<String>): String? =
        hints?.find(id)?.split('|')?.filter { it.isNotEmpty() && key(it) !in exclude }?.take(2)
            ?.takeIf { it.isNotEmpty() }?.joinToString(", ")

    /** The name of [lang] in [ui]'s language, with a capital letter. */
    fun languageName(lang: String, ui: Locale): String =
        Locale.forLanguageTag(lang).getDisplayLanguage(ui).replaceFirstChar { it.titlecase(ui) }
}

/**
 * The lines of a sorted file, found by binary search on what comes before the tab. The text stays
 * one string; only where each line starts is kept besides.
 */
internal class SortedLines(private val text: String) {
    private val starts: IntArray

    init {
        var n = 0
        for (c in text) if (c == '\n') n++
        if (text.isNotEmpty() && !text.endsWith('\n')) n++
        val s = IntArray(n)
        var i = 0
        var at = 0
        while (i < n) {
            s[i++] = at
            val nl = text.indexOf('\n', at)
            at = if (nl < 0) text.length else nl + 1
        }
        starts = s
    }

    val size: Int get() = starts.size

    private fun end(i: Int): Int = when {
        i + 1 < starts.size -> starts[i + 1] - 1
        text.endsWith('\n') -> text.length - 1
        else -> text.length
    }

    fun line(i: Int): String? = if (i in starts.indices) text.substring(starts[i], end(i)) else null

    /** Compares the key of line [i] (up to the tab) with [k]. */
    private fun compare(i: Int, k: String): Int {
        var a = starts[i]
        val e = end(i)
        var b = 0
        while (true) {
            val ca = if (a < e && text[a] != '\t') text[a] else null
            val cb = if (b < k.length) k[b] else null
            if (ca == null || cb == null) {
                return when {
                    ca == null && cb == null -> 0
                    ca == null -> -1
                    else -> 1
                }
            }
            if (ca != cb) return ca.compareTo(cb)
            a++
            b++
        }
    }

    /** What follows the tab on the line for [k], or null. */
    fun find(k: String): String? {
        var lo = 0
        var hi = starts.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val c = compare(mid, k)
            when {
                c < 0 -> lo = mid + 1
                c > 0 -> hi = mid - 1
                else -> {
                    val e = end(mid)
                    val tab = text.indexOf('\t', starts[mid])
                    return if (tab in 0 until e) text.substring(tab + 1, e) else ""
                }
            }
        }
        return null
    }
}
