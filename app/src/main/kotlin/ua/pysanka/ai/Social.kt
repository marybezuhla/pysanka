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

    /** How long a post is: letters and emoji, each counted once. */
    fun length(text: String): Int = text.trim().let { it.codePointCount(0, it.length) }
}

/**
 * Pysanka: a long post split into a thread, without the AI: whole sentences where they fit, else
 * whole words (a word longer than a post is cut), each part numbered "1/3" at its end.
 */
internal object ThreadSplit {
    fun split(text: String, limit: Int): List<String> {
        val t = text.trim()
        if (SocialApps.length(t) <= limit) return listOf(t)
        var n = 2
        while (true) {
            val suffix = " $n/$n".length
            val parts = pack(t, limit - suffix)
            if (parts.size <= n || n >= 999) return parts.mapIndexed { i, p -> "$p ${i + 1}/${parts.size}" }
            n = parts.size
        }
    }

    private fun len(s: String) = s.codePointCount(0, s.length)

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
        if (len(w) <= room) {
            listOf(w)
        } else {
            val cps = w.codePoints().toArray()
            cps.toList().chunked(room).map { c -> String(c.toIntArray(), 0, c.size) }
        }
    }
}
