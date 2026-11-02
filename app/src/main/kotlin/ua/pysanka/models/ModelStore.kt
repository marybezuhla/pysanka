package ua.pysanka.models

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Pysanka: a model file the person downloads themselves (Pysanka has no internet permission): one
 * exact published file, pinned by its revision and SHA-256.
 */
class PinnedModel(
    /** Where it is kept in Pysanka's private storage. */
    val dir: String,
    val name: String,
    /** The file name as it is downloaded (shown to the person). */
    val downloadName: String,
    /** The download link, at a fixed revision, so it is always the file with [sha256]. */
    val download: String,
    /** The exact size when known (a file of another size is refused at once); else the most it may be. */
    val bytes: Long?,
    val maxBytes: Long,
    val sha256: String,
    /** About how big, in GB, for the person. */
    val sizeGb: Double,
    /** About how much free space the copy needs, in GB. */
    val spaceGb: Double,
) {
    companion object {
        /** Gemma 4 E2B for the AI (the same file as in Tarilka and Mandat). */
        val GEMMA = PinnedModel(
            dir = "ai_model",
            name = "model.litertlm",
            downloadName = "gemma-4-E2B-it.litertlm",
            download = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/" +
                "1caa527c1e88b55f046fcd8d2b4265606680663d/gemma-4-E2B-it.litertlm?download=true",
            bytes = 2_588_147_712L,
            maxBytes = 2_588_147_712L,
            sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
            sizeGb = 2.6,
            spaceGb = 2.8,
        )

        /** Whisper small (OpenAI, MIT), whisper.cpp's 5-bit file, for dictation: the more accurate. */
        val WHISPER = PinnedModel(
            dir = "voice_model",
            name = "model.bin",
            downloadName = "ggml-small-q5_1.bin",
            download = "https://huggingface.co/ggerganov/whisper.cpp/resolve/" +
                "f281eb45af861ab5e5297d23694b7d46e090c02c/ggml-small-q5_1.bin?download=true",
            bytes = null,
            maxBytes = 200_000_000L,
            sha256 = "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb",
            sizeGb = 0.19,
            spaceGb = 0.4,
        )

        /** The Whisper called [name] ("base"; anything else: small). */
        fun whisper(name: String?): PinnedModel = if (name == "base") WHISPER_BASE else WHISPER

        /** Whisper base, whisper.cpp's 5-bit file: the faster (for older phones), less accurate. */
        val WHISPER_BASE = PinnedModel(
            dir = "voice_model_base",
            name = "model.bin",
            downloadName = "ggml-base-q5_1.bin",
            download = "https://huggingface.co/ggerganov/whisper.cpp/resolve/" +
                "f281eb45af861ab5e5297d23694b7d46e090c02c/ggml-base-q5_1.bin?download=true",
            bytes = null,
            maxBytes = 65_000_000L,
            sha256 = "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898",
            sizeGb = 0.06,
            spaceGb = 0.3,
        )
    }
}

/**
 * Pysanka: where a [PinnedModel] lives and how it gets there: the person downloads it in their
 * browser and picks the file; it is copied into Pysanka's private storage and accepted only if it
 * is exactly the published file (checked by SHA-256 while copying).
 */
class ModelStore(private val context: Context, val model: PinnedModel = PinnedModel.GEMMA) {

    sealed interface State {
        data object Missing : State
        data class Ready(val sizeMb: Long) : State
    }

    class WrongFile : Exception()
    class NoSpace : Exception()

    /** App-private storage: no other app can read or replace it, and it needs no permission. */
    private val dir: File get() = File(context.filesDir, model.dir).apply { mkdirs() }

    private fun fits(length: Long) = if (model.bytes != null) length == model.bytes else length in 1..model.maxBytes

    fun modelFile(): File? = File(dir, model.name).takeIf { it.isFile && fits(it.length()) }

    fun state(): State {
        val f = modelFile() ?: return State.Missing
        return State.Ready(f.length() / 1_000_000) // decimal MB, like the size on the download page
    }

    data class ImportResult(val originalDeleted: Boolean?)

    /**
     * Copies a model the person picked with the system file picker. When [deleteOriginal] is set,
     * tries to remove the picked file afterwards (null = not asked, false = the storage did not allow it).
     */
    suspend fun import(uri: Uri, deleteOriginal: Boolean, onProgress: (Float) -> Unit): ImportResult =
        withContext(Dispatchers.IO) {
            val cr = context.contentResolver
            val total = runCatching { cr.openAssetFileDescriptor(uri, "r")?.use { it.length } }.getOrNull() ?: -1L
            val tmp = File(dir, "import.tmp")
            tmp.delete() // a leftover from a failed copy must not block the space check
            // A file of another size is refused at once, not after copying gigabytes
            if (total > 0 && !fits(total)) throw WrongFile()
            if (dir.usableSpace < model.maxBytes + 200 * MB) throw NoSpace()
            val sha = java.security.MessageDigest.getInstance("SHA-256")
            try {
                cr.openInputStream(uri)!!.use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(1 shl 20)
                        var copied = 0L
                        while (true) {
                            // Stops when the screen that started it is gone; the temp file is removed below
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            sha.update(buf, 0, n)
                            copied += n
                            // A wrong, huge file must not fill the phone before the checksum says no
                            if (copied > model.maxBytes) throw WrongFile()
                            onProgress(copied.toFloat() / (if (total > 0) total else model.maxBytes))
                        }
                    }
                }
                val hex = sha.digest().joinToString("") { "%02x".format(it) }
                if (!fits(tmp.length()) || hex != model.sha256) throw WrongFile()
            } catch (t: Throwable) {
                tmp.delete()
                throw t
            }
            val target = File(dir, model.name)
            target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            val deleted = if (deleteOriginal) {
                runCatching { DocumentsContract.deleteDocument(cr, uri) }.getOrDefault(false)
            } else {
                null
            }
            ImportResult(deleted)
        }

    fun delete() {
        dir.listFiles()?.forEach { it.delete() }
        if (model === PinnedModel.GEMMA) {
            ua.pysanka.ai.AiEngine.cacheDir(context).deleteRecursively()
            ua.pysanka.ai.AiEntry.sync(context)
        }
    }

    /**
     * Removes what an interrupted copy left (a temporary or partial file). Called when the settings
     * screen opens, never while a copy runs.
     */
    fun cleanUp() {
        dir.listFiles()?.forEach { f -> if (f.name != model.name || !fits(f.length())) f.delete() }
    }

    private companion object {
        const val MB = 1024L * 1024L
    }
}
