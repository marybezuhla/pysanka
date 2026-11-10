package ua.pysanka.voice

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import ua.pysanka.whisper.WhisperNative
import java.util.concurrent.Executors

/**
 * Pysanka: dictation's speech recognition (Whisper), in its own process (":voice"): the model never
 * weighs on the keyboard, and it is freed when the keyboard unbinds. The keyboard records and sends
 * the sound here in short pieces; the text goes back in a message. The sound is kept in memory only
 * until it is recognised (then overwritten); nothing is written anywhere.
 *
 * Not exported: only Pysanka itself can bind.
 */
class VoiceService : Service() {
    companion object {
        // From the keyboard
        const val START = 1
        const val AUDIO = 2
        const val FINISH = 3
        const val CANCEL = 4

        // To the keyboard
        const val TEXT = 10
        const val FAIL = 11

        const val KEY_TOKEN = "token"
        const val KEY_LANGUAGE = "language"
        const val KEY_SAMPLES = "samples"
        const val KEY_TEXT = "text"
        const val KEY_REASON = "reason"

        /** The longest dictation at once. */
        const val MAX_SECONDS = 120
        private const val MAX_SAMPLES = MAX_SECONDS * WhisperNative.SAMPLE_RATE

        /** Louder than this somewhere, or it is taken as silence. */
        private const val SILENCE = 0.02f

        private val LANGUAGE = Regex("[a-z]{2,3}|auto")
    }

    private val main = Handler(Looper.getMainLooper())
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { handle(it); true })

    private var token = 0L
    private var language = "auto"
    private val pieces = ArrayList<ShortArray>()
    private var samples = 0
    /** The recognition running now (to stop it), 0 if none. */
    private var run = 0L

    override fun onBind(intent: Intent): IBinder = messenger.binder

    private fun handle(msg: Message) {
        val data = msg.data
        when (msg.what) {
            START -> {
                cancel()
                token = data.getLong(KEY_TOKEN)
                language = data.getString(KEY_LANGUAGE)?.takeIf { it.matches(LANGUAGE) } ?: "auto"
                // Loaded while the person speaks
                VoiceEngine.load(this)
            }
            AUDIO -> if (data.getLong(KEY_TOKEN) == token && run == 0L) {
                val piece = data.getShortArray(KEY_SAMPLES) ?: return
                // Past the limit the keyboard stops by itself; anything more is not kept
                if (samples + piece.size <= MAX_SAMPLES) {
                    pieces += piece
                    samples += piece.size
                } else {
                    piece.fill(0)
                }
            }
            FINISH -> msg.replyTo?.let { if (data.getLong(KEY_TOKEN) == token && run == 0L) finish(it, token) }
            CANCEL -> if (data.getLong(KEY_TOKEN) == token) cancel()
        }
    }

    private fun finish(reply: Messenger, token: Long) {
        val audio = FloatArray(samples)
        var i = 0
        var peak = 0f
        for (p in pieces) for (s in p) {
            val v = s / 32768f
            audio[i++] = v
            val a = kotlin.math.abs(v)
            if (a > peak) peak = a
        }
        clear()
        // Silence (or nothing at all): Whisper would make up words for it
        if (audio.size < WhisperNative.SAMPLE_RATE / 2 || peak < SILENCE) {
            audio.fill(0f)
            send(reply, FAIL, token) { putString(KEY_REASON, "silence") }
            return
        }
        val thisRun = WhisperNative.newRun()
        run = thisRun
        VoiceEngine.transcribe(audio, language, thisRun) { text ->
            main.post {
                if (run != thisRun) return@post
                run = 0L
                if (text == null) {
                    send(reply, FAIL, token) { putString(KEY_REASON, "failed") }
                } else {
                    send(reply, TEXT, token) { putString(KEY_TEXT, text) }
                }
            }
        }
    }

    private fun clear() {
        for (p in pieces) p.fill(0)
        pieces.clear()
        samples = 0
    }

    private fun cancel() {
        clear()
        if (run != 0L) WhisperNative.stop(run)
        run = 0L
    }

    private fun send(to: Messenger, what: Int, token: Long, fill: Bundle.() -> Unit) {
        val msg = Message.obtain(null, what)
        msg.data = Bundle().apply {
            putLong(KEY_TOKEN, token)
            fill()
        }
        // The keyboard is gone: nothing to tell
        runCatching { to.send(msg) }
    }

    override fun onDestroy() {
        cancel()
        VoiceEngine.release()
        super.onDestroy()
    }
}

/**
 * Pysanka: the model in the ":voice" process, one for the whole process and used on one thread
 * only, so it is never freed while it works and two services (one unbinding, the next binding)
 * never hold two models.
 */
private object VoiceEngine {
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "pysanka-whisper") }

    // Touched on the worker thread only
    private var model: WhisperNative.Model? = null

    /** Loads the downloaded model (once; see [VoiceModel]). */
    fun load(context: Context) {
        val app = context.applicationContext
        worker.execute {
            if (model != null) return@execute
            model = VoiceModel.map(app)?.let(WhisperNative::load)
        }
    }

    /** [done] (on the worker thread) gets the text, or null if it failed. */
    fun transcribe(audio: FloatArray, language: String, run: Long, done: (String?) -> Unit) {
        worker.execute {
            val m = model
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
            val text = try {
                runCatching { m?.transcribe(audio, language, Transcript.prompt(language), threads, run) }.getOrNull()
            } finally {
                audio.fill(0f)
            }
            done(text)
        }
    }

    /** Frees the model after whatever runs now (a new [load] loads it again). */
    fun release() {
        worker.execute {
            model?.close()
            model = null
        }
    }
}

/**
 * Pysanka: Whisper small (OpenAI, MIT; whisper.cpp's 5-bit file), downloaded by the person and
 * kept in Pysanka's private storage only if it is exactly the pinned file (see
 * [ua.pysanka.models.PinnedModel.WHISPER]). Mapped rather than read into memory.
 */
object VoiceModel {
    private fun store(context: Context) = ua.pysanka.models.ModelStore(context, ua.pysanka.models.PinnedModel.WHISPER)

    /** Whether the model has been added. */
    fun present(context: Context) = store(context).modelFile() != null

    /** The file, mapped; whisper copies what it needs while loading, then this can go. */
    fun map(context: Context): java.nio.ByteBuffer? = runCatching {
        val file = store(context).modelFile() ?: return null
        java.io.RandomAccessFile(file, "r").use { raf ->
            raf.channel.use { ch -> ch.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, 0, ch.size()) }
        }
    }.getOrNull()
}
