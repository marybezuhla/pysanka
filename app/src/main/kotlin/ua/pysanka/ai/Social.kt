package ua.pysanka.ai

/**
 * Pysanka: what the AI panel knows about the app being written in: its name and the most a post
 * there may have (characters, counted as letters and emoji; X counts links and some scripts
 * differently, so its count is close, not exact).
 */
internal object SocialApps {
    class App(val name: String, val limit: Int)

    private val APPS = mapOf(
        "com.twitter.android" to App("X", 280),
        "com.instagram.barcelona" to App("Threads", 500),
        "xyz.blueskyweb.app" to App("Bluesky", 300),
        "org.joinmastodon.android" to App("Mastodon", 500),
        "com.keylesspalace.tusky" to App("Mastodon", 500),
        "com.instagram.android" to App("Instagram", 2200),
        "com.linkedin.android" to App("LinkedIn", 3000),
    )

    fun of(packageName: String?): App? = packageName?.let(APPS::get)

    /** Every network once, for the list of limits. */
    val NETWORKS: List<App> = APPS.values.distinctBy { it.name }.sortedBy { it.limit }

    /** How long a post is: letters and emoji as seen, each counted once (👍🏽 or 🇺🇦 too). */
    fun length(text: String): Int = Graphemes.count(text.trim())
}

/**
 * Pysanka: characters as people see them: an emoji with its skin tone, joined emoji (👨‍👩‍👧), a flag,
 * a letter with its accent mark each count as one. (The same everywhere, not left to the Java
 * version's own rules.)
 */
internal object Graphemes {
    private fun joins(cp: Int) = cp == 0x200D || cp == 0xFE0F || cp == 0xFE0E || cp == 0x20E3 ||
        cp in 0x1F3FB..0x1F3FF || cp in 0xE0020..0xE007F ||
        Character.getType(cp).let { it == Character.NON_SPACING_MARK.toInt() || it == Character.ENCLOSING_MARK.toInt() || it == Character.COMBINING_SPACING_MARK.toInt() }

    private fun regional(cp: Int) = cp in 0x1F1E6..0x1F1FF

    /** Where each character starts, and the end. */
    private fun bounds(s: String): List<Int> {
        val out = mutableListOf(0)
        var i = 0
        var prev = -1
        var regionals = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val glued = i > 0 && (joins(cp) || prev == 0x200D || (regional(cp) && regional(prev) && regionals % 2 == 1))
            if (!glued && i > 0) out += i
            regionals = if (regional(cp)) (if (glued) regionals + 1 else 1) else 0
            prev = cp
            i += Character.charCount(cp)
        }
        if (s.isNotEmpty()) out += s.length
        return out
    }

    fun count(s: String): Int = if (s.isEmpty()) 0 else bounds(s).size - 1

    /** [s] in pieces of at most [size] characters, never cutting one. */
    fun chunks(s: String, size: Int): List<String> {
        val b = bounds(s)
        return (0 until b.size - 1 step size).map { i -> s.substring(b[i], b[minOf(i + size, b.size - 1)]) }
    }
}

/**
 * Pysanka: a long post split into a thread, without the AI: whole sentences where they fit, else
 * whole words (a word longer than a post is cut, never inside a character), each part numbered
 * "1/3" at its end. Line breaks become spaces (a part is one paragraph).
 */
internal object ThreadSplit {
    fun split(text: String, limit: Int): List<String> {
        val t = text.trim()
        if (SocialApps.length(t) <= limit) return listOf(t)
        var n = 2
        while (true) {
            val suffix = " $n/$n".length
            val parts = pack(t, (limit - suffix).coerceAtLeast(1))
            if (parts.size <= 1) return parts
            if (parts.size <= n || n >= 999) return parts.mapIndexed { i, p -> "$p ${i + 1}/${parts.size}" }
            n = parts.size
        }
    }

    private fun len(s: String) = Graphemes.count(s)
    /** The text in pieces of at most [room] characters, sentences kept whole where they fit. */
    private fun pack(text: String, room: Int): List<String> {
        val out = mutableListOf<String>()
        var cur = StringBuilder()
        fun flush() {
            if (cur.isNotBlank()) out += cur.toString().trim()
            cur = StringBuilder()
        }
        for (unit in sentences(text)) {
            val pieces = if (len(unit) <= room) listOf(unit) else words(unit, room)
            for (p in pieces) {
                val joined = if (cur.isEmpty()) p else "$cur $p"
                if (len(joined) <= room) {
                    cur = StringBuilder(joined)
                } else {
                    flush()
                    cur.append(p)
                }
            }
        }
        flush()
        return out
    }

    private val SENTENCE_END = Regex("""(?<=[.!?…])\s+|\n+""")

    private fun sentences(text: String) = text.split(SENTENCE_END).map { it.trim().replace(Regex("\\s+"), " ") }.filter { it.isNotEmpty() }

    /** A sentence too long for one part: by words, a word longer than a part cut (never inside an emoji). */
    private fun words(sentence: String, room: Int): List<String> = sentence.split(' ').flatMap { w ->
        if (len(w) <= room) listOf(w) else Graphemes.chunks(w, room)
    }
}
