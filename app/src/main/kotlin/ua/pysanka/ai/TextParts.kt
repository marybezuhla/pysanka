package ua.pysanka.ai

/**
 * Pysanka: with nothing selected, the AI panel can work on the sentence or the paragraph around the
 * cursor instead of the whole field. Ranges are within the field's text (end exclusive), trimmed of
 * the spaces around them; a part that is the same as the next larger one is left out.
 */
object TextParts {
    enum class Part { SENTENCE, PARAGRAPH, ALL }

    fun around(text: String, cursor: Int): Map<Part, IntRange> {
        if (cursor < 0 || cursor > text.length || text.isBlank()) return emptyMap()
        val all = trimmed(text, 0, text.length) ?: return emptyMap()
        val result = LinkedHashMap<Part, IntRange>()
        // The paragraph: between line breaks
        val pStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it < 0 || it >= cursor) 0 else it + 1 }
        val pEnd = text.indexOf('\n', cursor).let { if (it < 0) text.length else it }
        val paragraph = trimmed(text, pStart, pEnd) ?: return emptyMap()
        if (paragraph != all) result[Part.PARAGRAPH] = paragraph
        // The sentence within it: after . ! ? … (and closing quotes or brackets) and a space
        val inPara = text.substring(paragraph.first, paragraph.last + 1)
        val at = (cursor - paragraph.first).coerceIn(0, inPara.length)
        var sStart = 0
        var sEnd = inPara.length
        for (m in BOUNDARY.findAll(inPara)) {
            val end = m.range.first + m.groupValues[1].length
            val next = m.range.last + 1
            if (at >= next) {
                // The cursor is in a later sentence
                sStart = next
            } else {
                // In this sentence, right after its full stop, or in the spaces after it
                sEnd = end
                break
            }
        }
        val sentence = trimmed(text, paragraph.first + sStart, paragraph.first + sEnd)
        if (sentence != null && sentence != paragraph) result[Part.SENTENCE] = sentence
        result[Part.ALL] = all
        return result
    }

    private val BOUNDARY = Regex("""([.!?…]+["»”)\]]*)\s+""")

    private fun trimmed(text: String, start: Int, end: Int): IntRange? {
        var s = start
        var e = end
        while (s < e && text[s].isWhitespace()) s++
        while (e > s && text[e - 1].isWhitespace()) e--
        return if (s < e) s until e else null
    }
}
