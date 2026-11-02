package ua.pysanka.ocr

/**
 * Pysanka: text chosen on the "text from a photo" screen, waiting for the keyboard to come back to
 * the field it was opened from. Kept in memory only.
 *
 * It goes only into that same field (same app, field id, name, kind and hint: web and Compose apps
 * often give every field the same id), never into a password field, and
 * only within a minute; anything else drops it. Opening the screen again clears what was waiting.
 */
object PendingInsert {
    private const val MAX_AGE_MS = 60 * 1000L

    private data class Target(val packageName: String?, val fieldId: Int, val fieldName: String?, val inputType: Int, val hint: String?)

    private fun targetOf(info: dev.patrickgold.florisboard.ime.editor.FlorisEditorInfo) =
        Target(info.packageName, info.base.fieldId, info.base.fieldName, info.base.inputType, info.base.hintText?.toString())

    private var target: Target? = null
    private var text: String? = null
    private var at = 0L

    /** Called when the screen is opened from the keyboard: remembers the field, clears old text. */
    @Synchronized
    fun opened(info: dev.patrickgold.florisboard.ime.editor.FlorisEditorInfo) {
        target = targetOf(info)
        text = null
        fromPrivateField = ua.pysanka.privacy.FieldPrivacy.isPrivate || ua.pysanka.privacy.FieldPrivacy.isPassword
    }

    /** Opened from a password, incognito or no-learning field: its text never goes to the AI. */
    @Volatile
    var fromPrivateField = false
        private set

    /** Whether the screen was opened from a field (so there is somewhere to insert). */
    @Synchronized
    fun hasTarget(): Boolean = target != null

    @Synchronized
    fun set(value: String) {
        if (target == null) return
        text = value
        at = System.currentTimeMillis()
    }

    /** The waiting text if [info] is the field it was meant for; it is cleared either way. */
    @Synchronized
    fun takeFor(info: dev.patrickgold.florisboard.ime.editor.FlorisEditorInfo, isPassword: Boolean, isPrivate: Boolean = false): String? {
        val t = text ?: return null
        val wanted = target
        text = null
        target = null
        val fresh = System.currentTimeMillis() - at < MAX_AGE_MS
        return t.takeIf { fresh && !isPassword && !isPrivate && wanted == targetOf(info) }
    }
}

/** Pysanka: opens "text from a photo / the camera" from the keyboard (the keyboard hides meanwhile). */
fun openTextFromImage(source: String) {
    dev.patrickgold.florisboard.FlorisImeService.pysankaLaunch { TextFromImageActivity.intent(it, source) }
}
