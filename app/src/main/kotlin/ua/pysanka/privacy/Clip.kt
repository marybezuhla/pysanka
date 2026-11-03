package ua.pysanka.privacy

import android.content.ClipData
import android.content.Context

/** Pysanka: puts text in the system clipboard (one place for Pysanka's own "Copy" buttons). */
object Clip {
    /** Marks Pysanka's own copy from a private field: never kept in the history (as in incognito). */
    const val PRIVATE_EXTRA = "ua.pysanka.PRIVATE"

    /**
     * [sensitive]: from a private or password field: marked as passwords are (masked by the system)
     * and as private, so it stays out of the history even with "Don't keep passwords" off.
     */
    fun copy(context: Context, text: String, sensitive: Boolean = false) {
        val clip = ClipData.newPlainText("Pysanka", text)
        if (sensitive) {
            clip.description.extras = android.os.PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
                putBoolean(PRIVATE_EXTRA, true)
            }
        }
        context.getSystemService(android.content.ClipboardManager::class.java)?.setPrimaryClip(clip)
    }
}
