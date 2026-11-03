package ua.pysanka.typing

import android.content.Context

/**
 * Pysanka: which languages have a word list (assets/ime/dict/<language>.json): Ukrainian, English
 * and Russian. Another language typed with Pysanka (Polish, German…) has no list, so it gets no
 * corrections and no swipe (a swipe would draw its trail and type nothing); its text shortcuts and
 * the words the person taught still come as suggestions.
 */
object WordLists {
    @Volatile private var languages: Set<String>? = null

    fun has(context: Context, language: String): Boolean {
        val known = languages ?: (runCatching { context.assets.list("ime/dict") }.getOrNull().orEmpty()
            .filter { it.endsWith(".json") }.map { it.removeSuffix(".json") }.toSet()).also { languages = it }
        return language in known
    }
}
