package ua.pysanka.typing

import androidx.annotation.StringRes
import dev.patrickgold.florisboard.R

/**
 * Pysanka: the extra word lists a person can turn on (assets/ime/dict/<language>-<theme>.json,
 * made by scripts/dict/themes.py): offered and never "corrected", but behind everyday words.
 */
object ExtraDictionaries {
    enum class Theme(val id: String, @StringRes val label: Int) {
        SLANG("slang", R.string.pysanka__dicts__slang),
        MILITARY("military", R.string.pysanka__dicts__military),
        STATE("state", R.string.pysanka__dicts__state),
        BRANDS("brands", R.string.pysanka__dicts__brands),
        MEDICAL("medical", R.string.pysanka__dicts__medical),
        PLACES("places", R.string.pysanka__dicts__places),
        NAMES("names", R.string.pysanka__dicts__names),
    }

    /** The saved choice ("slang,military") as theme ids, in the themes' own order. */
    fun chosen(saved: String): List<String> {
        val ids = saved.split(',').map { it.trim() }.toSet()
        return Theme.entries.filter { it.id in ids }.map { it.id }
    }

    fun save(ids: Collection<String>): String = Theme.entries.filter { it.id in ids }.joinToString(",") { it.id }
}
