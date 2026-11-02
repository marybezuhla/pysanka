package ua.pysanka.voice

/**
 * Pysanka: what Whisper wrote, made ready for the field: without its notes on sounds
 * ("[BLANK_AUDIO]", "[музика]", "(сміється)", "*кашель*"), on one line, without extra spaces.
 */
internal object Transcript {
    private val NOTES = Regex("""\[[^\]]{1,40}]|\([^)]{1,40}\)|\*[^*]{1,40}\*""")
    private val SPACES = Regex("""\s+""")

    fun clean(raw: String, language: String = ""): String = raw.replace(NOTES, " ").replace(SPACES, " ").trim()
        // A note alone, or only punctuation left
        .takeIf { s -> s.any { it.isLetterOrDigit() } }
        // Whisper repeating (a long part of) its own sample, as it does for near-silence: nothing was
        // said ("Добрий день" alone is a real greeting, kept)
        ?.takeIf { it.length < 40 || !prompt(language).contains(it.trim('.', ' ')) }
        .orEmpty()

    /**
     * A sample of well-written text in [language] that Whisper reads before listening: it then
     * writes the same way, with commas only where the grammar needs them and full stops at the
     * end, instead of a comma at every pause. Empty for languages without one.
     */
    fun prompt(language: String): String = when (language) {
        "uk" -> "Добрий день. Пишу коротко й зрозуміло, ставлю коми там, де їх вимагає граматика, а в кінці речення крапку."
        "en" -> "Hello. I write short, clear sentences, with commas only where grammar needs them and a full stop at the end."
        else -> ""
    }
}
