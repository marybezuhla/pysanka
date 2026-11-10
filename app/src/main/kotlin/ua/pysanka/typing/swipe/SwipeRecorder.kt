package ua.pysanka.typing.swipe

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Pysanka: swipes recorded on the swipe practice screen, to tune the decoder on real fingers rather
 * than simulated ones. Only while that screen asks for a word ([target]); only words from its list
 * (never the person's own text). Each line: the word asked, the decoder's guesses, the swipe and the
 * key centres, all in key widths. Kept in Pysanka's files until saved by the person or deleted.
 */
object SwipeRecorder {
    class Shot(
        val xs: FloatArray, val ys: FloatArray,
        val keys: Map<Char, SwipeDecoder.Point>,
        val language: String,
        /** Pysanka's decoder answered (not FlorisBoard's, used while it is being built). */
        val decoder: Boolean,
    )

    /** The word the practice screen asks for while it is shown; null: nothing is recorded. */
    @Volatile var target: String? = null

    /** The practice field's mark (its private IME options): only a swipe into that field is recorded. */
    const val FIELD = "ua.pysanka.swipe_practice"

    /**
     * The word asked, only when the swipe went into the practice field itself (Pysanka's own, marked
     * field, not private, not the clipboard search): taken when the swipe ends, on the main thread.
     * Never anything typed elsewhere, even with the practice screen open behind or beside.
     */
    fun targetFor(info: dev.patrickgold.florisboard.ime.editor.FlorisEditorInfo, ownPackage: String): String? {
        val word = target ?: return null
        if (info.packageName != ownPackage || info.base.privateImeOptions != FIELD) return null
        if (ua.pysanka.privacy.FieldPrivacy.isPrivate || ua.pysanka.privacy.FieldPrivacy.isPassword || ua.pysanka.clipboard.ClipSearch.active) return null
        return word
    }

    private val _count = MutableStateFlow(-1)
    /** Swipes recorded so far (-1: not read yet). */
    val count: StateFlow<Int> = _count.asStateFlow()

    /** The last swipe recorded: the word asked and the decoder's first guess. */
    private val _last = MutableStateFlow<Pair<String, String?>?>(null)
    val last: StateFlow<Pair<String, String?>?> = _last.asStateFlow()

    private fun file(context: Context) = File(context.filesDir, "swipe_practice.jsonl")

    fun refresh(context: Context) {
        val f = file(context)
        _count.value = if (f.exists()) f.useLines { s -> s.count { it.isNotBlank() } } else 0
    }

    /** One swipe of [word] (asked when the swipe ended); [guesses]: the decoder's words, likeliest first. */
    @Synchronized
    fun record(context: Context, word: String, shot: Shot, guesses: List<String>) {
        // The list is Ukrainian: a swipe on another layout says nothing about it
        if (shot.language != "uk") return
        val line = JSONObject().apply {
            put("word", word)
            put("lang", shot.language)
            put("decoder", shot.decoder)
            put("guesses", JSONArray(guesses.take(8)))
            put("xs", JSONArray(shot.xs.map { round(it) }))
            put("ys", JSONArray(shot.ys.map { round(it) }))
            put("keys", JSONObject().apply { for ((c, p) in shot.keys) put(c.toString(), JSONArray(listOf(round(p.x), round(p.y)))) })
        }
        file(context).appendText(line.toString() + "\n")
        _last.value = word to guesses.firstOrNull()
        _count.value = _count.value.coerceAtLeast(0) + 1
    }

    private fun round(v: Float) = Math.round(v * 1000) / 1000.0

    /** Saved where the person chose (to pass on for tuning). */
    fun export(context: Context, to: Uri): Boolean = runCatching {
        val f = file(context)
        context.contentResolver.openOutputStream(to)?.use { out -> if (f.exists()) f.inputStream().use { it.copyTo(out) } } ?: return false
        true
    }.getOrDefault(false)

    fun clear(context: Context) {
        file(context).delete()
        _last.value = null
        refresh(context)
    }

    /** The practice words for [language] (Ukrainian only for now). */
    fun words(context: Context, language: String): List<String> = runCatching {
        context.assets.open("ime/swipe_training_$language.txt").bufferedReader().readLines().filter { it.isNotBlank() }
    }.getOrDefault(emptyList())
}
