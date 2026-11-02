package ua.pysanka.typing

/** Pysanka: the quote or apostrophe to type in place of a straight one (see KeyboardManager.pysankaSmartChar). */
object SmartPunctuation {
    /** After these (or at the start) a quote opens. */
    private const val OPENS_AFTER = "([{«„“—–-/"

    fun of(char: String, language: String, before: Char?): String {
        val opening = before == null || before.isWhitespace() || before in OPENS_AFTER
        return when (char) {
            "\"" -> when (language) {
                "uk" -> if (opening) "«" else "»"
                "en" -> if (opening) "“" else "”"
                else -> char
            }
            // The Ukrainian apostrophe is a letter (ʼ), so a word with it stays one word
            "'" -> if (language == "uk" && before?.isLetter() == true) "ʼ" else char
            else -> char
        }
    }
}
