package ua.pysanka.translate

import android.content.Context
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.subtypeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pysanka: which languages a translation is between, when the person has not chosen: from the
 * language of the word (see [Translations.detect]) into the person's own (the language of the
 * settings), or from it into another language of the keyboard.
 */
object TranslateChoice {
    /**
     * The person's own language: that of the settings (or else the phone's), if it is one of the
     * twelve; else English.
     */
    fun ownLanguage(context: Context): String {
        val chosen = runCatching {
            val prefs by FlorisPreferenceStore
            prefs.other.settingsLanguage.get()
        }.getOrNull()
        val ui = if (chosen.isNullOrBlank() || chosen == "auto") {
            context.resources.configuration.locales[0].language
        } else {
            dev.patrickgold.florisboard.lib.FlorisLocale.fromTag(chosen).language
        }
        return if (ui in Translations.LANGUAGES) ui else "en"
    }

    /** The keyboard's languages, the one typed in now first. */
    fun keyboardLanguages(context: Context): List<String> = runCatching {
        val sm = context.subtypeManager().value
        (listOf(sm.activeSubtype) + sm.subtypes).map { it.primaryLocale.language }
    }.getOrDefault(emptyList()).filter { it in Translations.LANGUAGES }.distinct()

    /** The language [from] is translated into, unless the person chose another this time. */
    fun target(context: Context, from: String): String {
        val chosen = runCatching {
            val prefs by FlorisPreferenceStore
            prefs.pysankaTranslate.target.get()
        }.getOrDefault("auto")
        if (chosen in Translations.LANGUAGES && chosen != from) return chosen
        val own = ownLanguage(context)
        if (own != from) return own
        return (keyboardLanguages(context) + listOf("uk", "en")).first { it != from }
    }

    data class Start(val from: String?, val to: String, val lookup: Translations.Lookup?)

    /** The language of [text], where it goes, and its translations; off the main thread. */
    suspend fun start(context: Context, text: String): Start = withContext(Dispatchers.Default) {
        val from = Translations.detect(Translations.source(context), text, keyboardLanguages(context) + ownLanguage(context))
        if (from == null) {
            Start(null, ownLanguage(context), null)
        } else {
            val to = target(context, from)
            Start(from, to, Translations.translate(Translations.source(context), text, from, to, ownLanguage(context)))
        }
    }

    suspend fun lookup(context: Context, text: String, from: String, to: String): Translations.Lookup =
        withContext(Dispatchers.Default) { Translations.translate(Translations.source(context), text, from, to, ownLanguage(context)) }
}
