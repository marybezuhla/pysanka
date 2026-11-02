package ua.pysanka.ai

/**
 * Pysanka: the possible replies the model wrote ([AiAction.REPLIES]), each on its own: separated by
 * blank lines, each starting with a short label and a colon («Так: …»). The label is shown, only
 * the reply itself goes into the field.
 */
internal object Replies {
    data class Reply(val label: String?, val text: String)

    /** At most this many characters before the colon count as a label («Уточнити:», "Clarify:"). */
    private const val LABEL_MAX = 20

    /** A short label, not the start of a reply that has a colon in it («Буду о 10:00»). */
    private fun isLabel(s: String) = s.trim('*', ' ').let { l ->
        l.isNotEmpty() && l.none { it.isDigit() || it == ',' || it == '\n' } && l.split(' ').size <= 3
    }

    private fun label(block: String): String? {
        val colon = block.indexOf(':')
        return block.takeIf { colon in 1..LABEL_MAX }?.substring(0, colon)?.takeIf { isLabel(it) }
    }

    fun parse(answer: String): List<Reply> {
        val blocks = answer.trim().split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }
            // Replies one per line, without blank lines between them
            .flatMap { b -> b.lines().takeIf { ls -> ls.size > 1 && ls.all { label(it) != null } } ?: listOf(b) }
        return blocks.map { block ->
            val label = label(block)
            val text = if (label != null) block.substring(block.indexOf(':') + 1).trim() else block
            // Plain text, without the model's own bold markup
            Reply(label?.trim('*', ' '), text.trim('*', ' ', '\n'))
        }.filter { it.text.isNotEmpty() }
    }

    /** The networks a post is written for ([AiAction.VERSIONS]), as the prompt names them. */
    private val NETWORKS = listOf("X", "Bluesky", "Threads", "LinkedIn")

    /** A line that starts a version: «**X:** …», "Bluesky:", "### Threads", "LinkedIn (1300):". */
    private val HEADER = Regex("""^\s*(?:#{1,4}\s+)?\**\s*([^:*\n]{1,30}?)\s*\**\s*(?::|$)\s*\**\s*(.*)$""")

    private fun network(label: String): String? = NETWORKS.firstOrNull { n ->
        // «X (Twitter)», «X / Twitter», but not a sentence that starts with the name («X is …»)
        label.startsWith(n, ignoreCase = true) && label.substring(n.length).trim().let { it.isEmpty() || it[0] in "(/–—-" }
    }

    /**
     * The versions of a post: each starts at a line naming its network; the lines after it (a long
     * post has paragraphs) belong to it until the next network (each network once; a hashtag
     * such as «#LinkedIn» is not a heading). Text before the first and after a closing «---» is
     * dropped.
     */
    fun versions(answer: String): List<Reply> {
        val out = mutableListOf<Pair<String, StringBuilder>>()
        var ended = false
        for (line in answer.lines()) {
            val m = HEADER.find(line)
            val name = m?.let { network(it.groupValues[1].trim()) }?.takeIf { n -> out.none { it.first == n } }
            val heading = line.contains(':') || Regex("""^\s*#{1,4}\s""").containsMatchIn(line) || line.contains("**")
            if (m != null && name != null && heading) {
                out += name to StringBuilder(m.groupValues[2])
                ended = false
            } else if (Regex("""^\s*(?:-{3,}|\*{3,}|_{3,})\s*$""").matches(line)) {
                ended = true
            } else if (!ended) {
                out.lastOrNull()?.second?.append('\n')?.append(line)
            }
        }
        return out.map { (n, b) -> Reply(n, b.toString().trim().trim('*').trim()) }.filter { it.text.isNotEmpty() }
    }
}
