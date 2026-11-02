package ua.pysanka.voice

/**
 * Pysanka: what Whisper wrote, made ready for the field: without its notes on sounds
 * ("[BLANK_AUDIO]", "[музика]", "(сміється)", "*кашель*"), on one line, without extra spaces.
 */
internal object Transcript {
    private val NOTES = Regex("""\[[^\]]{1,40}]|\([^)]{1,40}\)|\*[^*]{1,40}\*""")
    private val SPACES = Regex("""\s+""")

    fun clean(raw: String): String = raw.replace(NOTES, " ").replace(SPACES, " ").trim()
        // A note alone, or only punctuation left
        .takeIf { s -> s.any { it.isLetterOrDigit() } }.orEmpty()
}
