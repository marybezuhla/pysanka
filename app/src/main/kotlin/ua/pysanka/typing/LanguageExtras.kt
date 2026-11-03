package ua.pysanka.typing

import android.content.Context
import org.json.JSONObject

/**
 * Pysanka: per-language lists kept in assets/ime/dict/extras.json (made by scripts/dict/extras.py):
 * swear words and slurs ([Offensive]) and little words that never become a hashtag or an emoji, each
 * for its own language only (an ordinary word of one language can be a swear word or a little word of
 * another: French «car» is "because"). Read once, the first time words are suggested.
 */
object LanguageExtras {
    class Offensive(val stems: List<String>, val words: Set<String>)

    @Volatile var offensive: Map<String, Offensive> = emptyMap()
        private set
    @Volatile private var stopByLanguage: Map<String, Set<String>> = emptyMap()

    /** The little words of [language] (empty for a language without a list). */
    fun stop(language: String?): Set<String> = language?.let { stopByLanguage[it] }.orEmpty()
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
                val st = json.getJSONObject("stop")
                stopByLanguage = st.keys().asSequence().associateWith { strings(st.optJSONArray(it)).toHashSet() }
            }.onFailure {
                // Without the lists swear words could be offered: said in the log (only the error's kind)
                android.util.Log.w("Pysanka", "extras.json not read: ${it.javaClass.simpleName}")
            }
            loaded = true
        }
    }
}
