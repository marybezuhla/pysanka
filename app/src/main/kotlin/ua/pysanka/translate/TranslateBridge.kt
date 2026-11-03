package ua.pysanka.translate

import android.view.inputmethod.InputConnection
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.R
import org.florisboard.lib.android.showShortToastSync

/**
 * Pysanka: between the field and the translate panel. The word is read when the translate button
 * is pressed and kept only for the panel; nothing of it is stored. Not in password fields.
 */
object TranslateBridge {
    /** The word (or the selection) and how much of it is before and after the cursor. */
    data class Word(val text: String, val selected: Boolean, val before: Int, val after: Int, val trailing: String)

    private var word: Word? = null

    private fun ic(): InputConnection? = FlorisImeService.currentInputConnection()

    private fun isWordChar(c: Char) = c.isLetter() || c == '\'' || c == '’' || c == 'ʼ' || c == '-'

    /** Reads the word for the panel; false (with a message) where the panel cannot open. */
    fun open(): Boolean {
        if (ua.pysanka.privacy.FieldPrivacy.isPassword) {
            dev.patrickgold.florisboard.FlorisApplication.getHack().showShortToastSync(R.string.pysanka__translate__not_here)
            return false
        }
        word = read()
        return true
    }

    /** The panel takes the word once. */
    fun take(): Word? = word.also { word = null }

    private fun read(): Word? {
        val ic = ic() ?: return null
        val selected = ic.getSelectedText(0)?.toString()
        if (!selected.isNullOrBlank()) {
            return selected.takeIf { it.length <= Translations.MAX_CHARS }?.let { Word(it, true, 0, 0, "") }
        }
        val before = ic.getTextBeforeCursor(64, 0)?.toString().orEmpty()
        val after = ic.getTextAfterCursor(64, 0)?.toString().orEmpty()
        var b = before.takeLastWhile(::isWordChar)
        var a = after.takeWhile(::isWordChar)
        var trailing = ""
        if (b.isEmpty() && a.isEmpty()) {
            // Just after a word and its space: that word
            trailing = before.takeLastWhile { it == ' ' }
            b = before.dropLast(trailing.length).takeLastWhile(::isWordChar)
            a = ""
            if (trailing.length > 1) return null
        }
        val text = (b + a).trim('-', '\'', '’', 'ʼ')
        if (text.isEmpty()) return null
        return Word(b + a, false, b.length + trailing.length, a.length, trailing)
    }

    /**
     * Puts [translation] in place of [w], if the field still has it there; a capital letter
     * stays a capital.
     */
    fun replace(w: Word, translation: String): Boolean {
        val ic = ic() ?: return false
        val text = matchCase(w.text, translation)
        ic.beginBatchEdit()
        try {
            ic.finishComposingText()
            if (w.selected) {
                if (ic.getSelectedText(0)?.toString() != w.text) return false
                return ic.commitText(text, 1)
            }
            val before = ic.getTextBeforeCursor(w.before, 0)?.toString()
            val after = ic.getTextAfterCursor(w.after, 0)?.toString()
            val beforeWord = w.text.substring(0, w.before - w.trailing.length)
            if (before != beforeWord + w.trailing || after != w.text.substring(beforeWord.length)) return false
            ic.deleteSurroundingText(w.before, w.after)
            return ic.commitText(text + w.trailing, 1)
        } finally {
            ic.endBatchEdit()
        }
    }
}
