package ua.pysanka.typing

import android.content.Context
import org.json.JSONObject

/**
 * Pysanka: per-language lists kept in assets/ime/dict/extras.json (made by scripts/dict/extras.py):
 * swear words and slurs of each language ([Offensive]: only that language's, since an ordinary word of
 * one language can be a swear word in another) and little words of all languages that never become a
 * hashtag or an emoji. Read once, the first time words are suggested.
 */
object LanguageExtras {
    class Offensive(val stems: List<String>, val words: Set<String>)

    @Volatile var offensive: Map<String, Offensive> = emptyMap()
        private set
    @Volatile var stop: Set<String> = emptySet()
        private set
    @Volatile private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            runCatching {
                val json = JSONObject(context.assets.open("ime/dict/extras.json").bufferedReader().use { it.readText() })
                fun strings(a: org.json.JSONArray?) = a?.let { List(it.length()) { i -> it.getString(i) } }.orEmpty()
                val off = json.getJSONObject("offensive")
                offensive = off.keys().asSequence().associateWith { lang ->
                    val o = off.getJSONObject(lang)
                    Offensive(strings(o.optJSONArray("stems")), strings(o.optJSONArray("words")).toHashSet())
                }
                stop = strings(json.optJSONArray("stop")).toHashSet()
            }
            loaded = true
        }
    }
}
