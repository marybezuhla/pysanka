package ua.pysanka.typing

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShortText
import androidx.compose.ui.graphics.vector.ImageVector
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.SuggestionProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ua.pysanka.clipboard.SecureClipboard
import ua.pysanka.security.ClipboardVault
import ua.pysanka.security.SealedFile
import ua.pysanka.security.writeWhole
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * Pysanka: text replacement, as on the iPhone: a short word the person chose («дд») offered as its
 * text («Добрий день!»), put in place of it by a space or a tap. The person makes them in the
 * settings; they are kept in one file sealed with the vault's own key for them (AES-256-GCM).
 * Never in password fields.
 */
object TextShortcuts {
    const val MAX = 300
    const val MAX_SHORTCUT = 24
    const val MAX_TEXT = 2000
    private const val FILE = "text_shortcuts.bin"
    private val LABEL = "text-shortcuts".toByteArray()

    data class Shortcut(val shortcut: String, val text: String)

    /** All of them, in the order made; null until read (the vault is closed before the first unlock). */
    val all: StateFlow<List<Shortcut>?>
        field = MutableStateFlow(null)

    @Volatile private var byKey: Map<String, String> = emptyMap()
    private var lastTry = 0L

    /** A shortcut as typed: letters and digits only, with no spaces. */
    fun isValidShortcut(s: String) = s.length in 1..MAX_SHORTCUT && s.all { it.isLetterOrDigit() }

    /** The text for [typed] if it is a shortcut (any case); never reads a file (for the keyboard). */
    fun find(typed: String): String? = if (typed.isEmpty()) null else byKey[typed.lowercase()]

    /** Reads them (once the vault is open; a locked phone is tried again after a while). Off the main thread. */
    @Synchronized
    fun load(context: Context) {
        if (all.value != null) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (!ClipboardVault.isOpen() && lastTry != 0L && now - lastTry < 30_000L) return
        lastTry = now
        if (!SecureClipboard.ensureOpen(context) || !ClipboardVault.isOpen()) return
        val list = runCatching {
            val file = file(context)
            if (!file.isFile) return@runCatching emptyList()
            val bytes = file.inputStream().use { SealedFile.readAll(ClipboardVault.shortcutsKey(), LABEL, it) }
            try { decode(bytes) } finally { bytes.fill(0) }
        }.getOrDefault(emptyList())
        set(list)
    }

    /** Keeps [list] (the settings' whole list). Off the main thread; false if it could not be kept. */
    @Synchronized
    fun save(context: Context, list: List<Shortcut>): Boolean {
        if (!ClipboardVault.isOpen()) return false
        val clean = list.filter { isValidShortcut(it.shortcut) && it.text.isNotBlank() }
            .map { it.copy(text = it.text.take(MAX_TEXT)) }
            .distinctBy { it.shortcut.lowercase() }.take(MAX)
        val bytes = encode(clean)
        return try {
            writeWhole(file(context)) { out -> SealedFile.writer(ClipboardVault.shortcutsKey(), LABEL, out).use { it.write(bytes) } }
            set(clean)
            true
        } catch (e: Exception) {
            false
        } finally {
            bytes.fill(0)
        }
    }

    /** Removes them from memory and the phone (also when the vault's key is lost). */
    @Synchronized
    fun wipe(context: Context) {
        file(context).delete()
        all.value = null
        byKey = emptyMap()
    }

    private fun set(list: List<Shortcut>) {
        all.value = list
        byKey = list.associate { it.shortcut.lowercase() to it.text }
    }

    private fun file(context: Context) = File(context.applicationContext.noBackupFilesDir, FILE)

    internal fun encode(list: List<Shortcut>): ByteArray = ByteArrayOutputStream().also { b ->
        DataOutputStream(b).use { out ->
            out.writeShort(list.size)
            for (s in list) {
                out.writeUTF(s.shortcut)
                out.writeUTF(s.text)
            }
        }
    }.toByteArray()

    internal fun decode(bytes: ByteArray): List<Shortcut> = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        val n = input.readUnsignedShort()
        require(n <= MAX)
        List(n) { Shortcut(input.readUTF(), input.readUTF()) }
    }
}

/** Pysanka: a shortcut's text in the suggestions; applied by a space, like a correction. */
class ShortcutCandidate(val shortcut: String, override val text: CharSequence) : SuggestionCandidate {
    override val secondaryText: CharSequence? = null
    override val confidence = 1.0
    override val isEligibleForAutoCommit = true
    override val isEligibleForUserRemoval = false
    override val icon: ImageVector = Icons.Default.ShortText
    // No provider: it is never learned as a word
    override val sourceProvider: SuggestionProvider? = null
}
