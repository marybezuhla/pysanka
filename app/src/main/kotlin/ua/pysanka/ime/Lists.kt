package ua.pysanka.ime

import android.view.inputmethod.InputConnection
import dev.patrickgold.florisboard.FlorisImeService
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Pysanka: lists, to-dos and the date, as in note apps (Joplin, Samsung Notes):
 * - a list item, a numbered item or a to-do at the start of the line the cursor is in (tapped again:
 *   taken away; a to-do tapped again is ticked ☐ → ☑ and back);
 * - Enter after an item starts the next one («2.» after «1.»); Enter on an empty item ends the list;
 * - the date and time in the keyboard's language.
 *
 * In Markdown note apps (Joplin, Obsidian, Markor, Logseq) the items are Markdown ("- ", "- [ ] "),
 * which the app shows as its own lists and boxes; elsewhere they are characters every app shows
 * (•, ☐, ☑). Only the line being written is read; nothing is kept.
 */
object Lists {
    enum class Kind { BULLET, NUMBER, TODO }

    /** How items are written: [auto] by the app, or always as characters or as Markdown. */
    enum class Style { AUTO, SYMBOLS, MARKDOWN }

    private val MARKDOWN_APPS = setOf(
        "net.cozic.joplin", "md.obsidian", "net.gsantner.markor", "com.logseq.app",
    )

    /** An item at the start of a line: its indent, what kind, the marker as written, whether ticked. */
    data class Item(val indent: String, val kind: Kind, val marker: String, val number: Int = 0, val done: Boolean = false) {
        val prefix get() = indent + marker
    }

    private val TODO_MD = Regex("^(\\s*)([-*]) \\[([ xX])] ")
    private val TODO_SYMBOL = Regex("^(\\s*)([☐☑]) ")
    private val NUMBER = Regex("^(\\s*)(\\d{1,3})([.)]) ")
    private val BULLET_SYMBOL = Regex("^(\\s*)([•◦▪]) ")
    private val BULLET_MD = Regex("^(\\s*)([-*+]) ")

    /**
     * The item [line] starts with. "- " and "* " count only in Markdown: elsewhere a line may start
     * with a dash for other reasons (a line of dialogue).
     */
    fun parse(line: String, markdown: Boolean): Item? {
        TODO_MD.find(line)?.let { m ->
            if (markdown) return Item(m.groupValues[1], Kind.TODO, m.value.substring(m.groupValues[1].length), done = m.groupValues[3] != " ")
        }
        TODO_SYMBOL.find(line)?.let { m -> return Item(m.groupValues[1], Kind.TODO, m.groupValues[2] + " ", done = m.groupValues[2] == "☑") }
        NUMBER.find(line)?.let { m ->
            return Item(m.groupValues[1], Kind.NUMBER, m.value.substring(m.groupValues[1].length), number = m.groupValues[2].toInt())
        }
        BULLET_SYMBOL.find(line)?.let { m -> return Item(m.groupValues[1], Kind.BULLET, m.groupValues[2] + " ") }
        if (markdown) BULLET_MD.find(line)?.let { m -> return Item(m.groupValues[1], Kind.BULLET, m.groupValues[2] + " ") }
        return null
    }

    /** The marker that follows [item] on the next line (a to-do starts unticked, a number goes up). */
    fun next(item: Item, markdown: Boolean): String = item.indent + when (item.kind) {
        Kind.BULLET -> item.marker
        Kind.NUMBER -> "${item.number + 1}${item.marker.trim().last()} "
        Kind.TODO -> if (markdown) "${item.marker.first()} [ ] " else "☐ "
    }

    /** A new item's marker; a number follows the item on the line before, if it is numbered. */
    fun marker(kind: Kind, markdown: Boolean, previous: Item?): String = when (kind) {
        Kind.BULLET -> if (markdown) "- " else "• "
        Kind.NUMBER -> if (previous?.kind == Kind.NUMBER) "${previous.number + 1}. " else "1. "
        Kind.TODO -> if (markdown) "- [ ] " else "☐ "
    }

    /** [item] ticked or unticked. */
    fun toggled(item: Item): String = when {
        item.marker.startsWith("☐") -> "☑ "
        item.marker.startsWith("☑") -> "☐ "
        item.done -> item.marker.replace(Regex("\\[[xX]]"), "[ ]")
        else -> item.marker.replace("[ ]", "[x]")
    }

    /**
     * What the line becomes when [kind] is chosen on it: the text after the cursor is not part of
     * it. Returns the new beginning of the line (up to the cursor).
     */
    fun apply(lineBefore: String, kind: Kind, markdown: Boolean, previousLine: String?): String {
        val item = parse(lineBefore, markdown)
        val previous = previousLine?.let { parse(it, markdown) }
        return when {
            item == null -> {
                val indent = lineBefore.takeWhile { it == ' ' || it == '\t' }
                indent + marker(kind, markdown, previous) + lineBefore.substring(indent.length)
            }
            // A to-do chosen on a to-do: ticked or unticked
            item.kind == Kind.TODO && kind == Kind.TODO -> item.indent + toggled(item) + lineBefore.substring(item.prefix.length)
            // The same kind again: taken away
            item.kind == kind -> item.indent + lineBefore.substring(item.prefix.length)
            // Another kind: in its place
            else -> item.indent + marker(kind, markdown, previous) + lineBefore.substring(item.prefix.length)
        }
    }

    private fun ic(): InputConnection? = FlorisImeService.currentInputConnection()

    fun isMarkdown(packageName: String?): Boolean {
        val style = runCatching {
            val prefs by dev.patrickgold.florisboard.app.FlorisPreferenceStore
            prefs.pysankaLists.style.get()
        }.getOrDefault(Style.AUTO.name)
        return when (style) {
            Style.MARKDOWN.name -> true
            Style.SYMBOLS.name -> false
            else -> packageName in MARKDOWN_APPS
        }
    }

    /** [kind] at the start of the line the cursor is in (see [apply]). */
    fun insert(kind: Kind, packageName: String?): Boolean {
        val ic = ic() ?: return false
        ic.beginBatchEdit()
        try {
            ic.finishComposingText()
            val before = ic.getTextBeforeCursor(2000, 0)?.toString() ?: return false
            val line = before.substringAfterLast('\n')
            val previousLine = before.dropLast(line.length).removeSuffix("\n").takeIf { before.contains('\n') }?.substringAfterLast('\n')
            val changed = apply(line, kind, isMarkdown(packageName), previousLine)
            if (changed == line) return false
            ic.deleteSurroundingText(line.length, 0)
            return ic.commitText(changed, 1)
        } finally {
            ic.endBatchEdit()
        }
    }

    /**
     * Enter in a list: the next item, or on an empty item the end of the list. False when the
     * line is no item (Enter does what it always does).
     */
    fun onEnter(packageName: String?): Boolean {
        val on = runCatching {
            val prefs by dev.patrickgold.florisboard.app.FlorisPreferenceStore
            prefs.pysankaLists.continueOnEnter.get()
        }.getOrDefault(true)
        if (!on || ua.pysanka.privacy.FieldPrivacy.isPassword) return false
        val ic = ic() ?: return false
        val before = ic.getTextBeforeCursor(2000, 0)?.toString() ?: return false
        val line = before.substringAfterLast('\n')
        val markdown = isMarkdown(packageName)
        val item = parse(line, markdown) ?: return false
        ic.beginBatchEdit()
        try {
            ic.finishComposingText()
            return if (line.substring(item.prefix.length).isBlank()) {
                // An empty item: the list ends here, the line is left empty
                ic.deleteSurroundingText(line.length, 0)
                true
            } else {
                ic.commitText("\n" + next(item, markdown), 1)
            }
        } finally {
            ic.endBatchEdit()
        }
    }

    enum class When { DATE, TIME, DATE_TIME }

    /** The date and/or time now, as written in [locale] («3 листопада 2026 р.», «14:05»). */
    fun now(what: When, locale: Locale, at: LocalDateTime = LocalDateTime.now()): String {
        val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
        val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
        return when (what) {
            When.DATE -> at.format(date)
            When.TIME -> at.format(time)
            When.DATE_TIME -> at.format(date) + ", " + at.format(time)
        }
    }

    /** Puts [text] at the cursor. */
    fun type(text: String): Boolean = FieldText.insert(text)
}
