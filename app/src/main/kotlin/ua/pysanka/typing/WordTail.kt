package ua.pysanka.typing

/**
 * Pysanka: the rest of the word after the cursor. A suggestion tapped with the cursor inside a word
 * replaces the whole word, not only its part before the cursor («Взаг|плі» → «Взагалі», not
 * «Взагаліплі»).
 */
object WordTail {
    private fun isMark(c: Char) = Character.getType(c) == Character.NON_SPACING_MARK.toInt() ||
        Character.getType(c) == Character.COMBINING_SPACING_MARK.toInt()

    // A hyphen or an apostrophe only inside the word (followed by a letter): not a dash or a
    // closing quote after it
    private fun isJoiner(c: Char) = c == '-' || c == '\'' || c == '’' || c == 'ʼ'

    /** How many characters (UTF-16) right after the cursor still belong to the word. */
    fun length(after: CharSequence): Int {
        var n = 0
        while (n < after.length) {
            val c = after[n]
            val ok = when {
                // First: ʼ counts as a letter to Java
                isJoiner(c) -> n + 1 < after.length && after[n + 1].isLetter()
                c.isLetterOrDigit() || isMark(c) || c.isLowSurrogate() -> true
                else -> false
            }
            if (!ok) break
            n++
        }
        // A combining mark or a low surrogate left over would break a character: never cut one in half
        while (n < after.length && (Character.getType(after[n]) == Character.NON_SPACING_MARK.toInt() || after[n].isLowSurrogate())) n++
        return n
    }
}
