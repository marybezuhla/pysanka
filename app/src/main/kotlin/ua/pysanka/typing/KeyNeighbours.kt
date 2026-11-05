package ua.pysanka.typing

/**
 * Pysanka: which letters sit next to each other on the layouts Pysanka sets up for the languages
 * with a word list (ЙЦУКЕН, QWERTY, QWERTZ, AZERTY and their national variants), so a slip onto a
 * neighbouring key counts as a likelier typing error.
 */
object KeyNeighbours {
    private val UK = listOf("йцукенгшщзхї", "фівапролджє", "ячсмитьбю")
    private val EN = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    private val RU = listOf("йцукенгшщзх", "фывапролджэ", "ячсмитьбю")
    private val BE = listOf("йцукенгшўзх", "фывапролджэ", "ячсмітьбю")
    private val QWERTZ = listOf("qwertzuiop", "asdfghjkl", "yxcvbnm")
    private val AZERTY = listOf("azertyuiop", "qsdfghjklm", "wxcvbn")
    private val HU = listOf("qwertzuiopö", "asdfghjkléá", "yxcvbnmü")
    private val ES = listOf("qwertyuiop", "asdfghjklñ", "zxcvbnm")

    fun forLanguage(language: String): Map<Char, String> = when (language) {
        "uk" -> build(UK)
        "en" -> build(EN)
        "ru" -> build(RU)
        "be" -> build(BE)
        // QWERTY with letters with marks in the popups
        "pl", "ro", "pt" -> build(EN)
        "es" -> build(ES)
        "de", "sk" -> build(QWERTZ)
        "hu" -> build(HU)
        "fr" -> build(AZERTY)
        else -> emptyMap()
    }

    private fun build(rows: List<String>): Map<Char, String> {
        val out = HashMap<Char, StringBuilder>()
        for ((r, row) in rows.withIndex()) {
            for ((i, c) in row.withIndex()) {
                val sb = out.getOrPut(c) { StringBuilder() }
                // Left and right, and the keys above and below (rows are shifted by about half a key)
                row.getOrNull(i - 1)?.let(sb::append)
                row.getOrNull(i + 1)?.let(sb::append)
                for (other in listOfNotNull(rows.getOrNull(r - 1), rows.getOrNull(r + 1))) {
                    other.getOrNull(i)?.let(sb::append)
                    other.getOrNull(i - 1)?.let(sb::append)
                    other.getOrNull(i + 1)?.let(sb::append)
                }
            }
        }
        // ґ is on the key of г; ё on е and ъ on ь (in their popups)
        out["г".first()]?.append('ґ')
        out["е".first()]?.append('ё')
        out["ь".first()]?.append('ъ')
        return out.mapValues { it.value.toString() }
    }
}
