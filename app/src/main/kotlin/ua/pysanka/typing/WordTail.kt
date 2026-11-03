package ua.pysanka.typing

/**
 * Pysanka: the rest of the word after the cursor. A suggestion tapped with the cursor inside a word
 * replaces the whole word, not only its part before the cursor («Взаг|плі» → «Взагалі», not
 * «Взагаліплі»).
 */
object WordTail {
    private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '\'' || c == '’' || c == 'ʼ' ||
        // A hyphen only inside the word (followed by a letter), not a dash after it
        c == '-'

    /** How many characters (UTF-16) right after the cursor still belong to the word. */
    fun length(after: CharSequence): Int {
        var n = 0
        while (n < after.length && isWordChar(after[n])) {
            if (after[n] == '-' && (n + 1 >= after.length || !after[n + 1].isLetter())) break
            n++
        }
        // A combining mark or a low surrogate left over would break a character: never cut one in half
        while (n < after.length && (Character.getType(after[n]) == Character.NON_SPACING_MARK.toInt() || after[n].isLowSurrogate())) n++
        return n
    }
}
