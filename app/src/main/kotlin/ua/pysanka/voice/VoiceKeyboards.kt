package ua.pysanka.voice

import android.content.Context
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype

/**
 * Pysanka: other apps' voice keyboards on this phone (a keyboard with a "voice" subtype, such as
 * FUTO Voice Input or Sayboard), for those who would rather dictate with one of them. Pysanka only
 * switches to it: the sound and the text are that app's, never Pysanka's.
 */
object VoiceKeyboards {
    /** [enabled]: turned on in the phone's keyboard settings (only then can Pysanka switch to it). */
    data class Keyboard(val id: String, val label: String, val enabled: Boolean)

    fun list(context: Context): List<Keyboard> {
        val imm = context.getSystemService(InputMethodManager::class.java) ?: return emptyList()
        val enabled = runCatching { imm.enabledInputMethodList.map { it.id }.toSet() }.getOrDefault(emptySet())
        return runCatching { imm.inputMethodList }.getOrDefault(emptyList())
            .filter { it.packageName != context.packageName && voiceSubtype(it) != null }
            .map { Keyboard(it.id, it.loadLabel(context.packageManager).toString(), it.id in enabled) }
            .sortedBy { it.label.lowercase() }
    }

    fun find(context: Context, id: String): Keyboard? = list(context).firstOrNull { it.id == id }

    fun voiceSubtype(info: InputMethodInfo): InputMethodSubtype? =
        (0 until info.subtypeCount).map { info.getSubtypeAt(it) }.firstOrNull { it.mode == "voice" }
}
