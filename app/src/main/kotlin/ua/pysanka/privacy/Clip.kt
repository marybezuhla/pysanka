package ua.pysanka.privacy

import android.content.ClipData
import android.content.Context

/** Pysanka: puts text in the system clipboard (one place for Pysanka's own "Copy" buttons). */
object Clip {
    /** [sensitive]: from a private or password field, marked as passwords are (kept out of the history). */
    fun copy(context: Context, text: String, sensitive: Boolean = false) {
        val clip = ClipData.newPlainText("Pysanka", text)
        if (sensitive) {
            clip.description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        }
        context.getSystemService(android.content.ClipboardManager::class.java)?.setPrimaryClip(clip)
    }
}
