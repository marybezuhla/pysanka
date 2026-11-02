package ua.pysanka.voice

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.pysanka.models.ModelStore
import ua.pysanka.models.PinnedModel
import ua.pysanka.whisper.WhisperNative
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Pysanka: dictation's speech recognition (Whisper), in its own process (":voice"): the model never
 * weighs on the keyboard, and it is freed when the keyboard unbinds. The keyboard records and sends
 * the sound here in short pieces; the text goes back in a message. The sound is kept in memory only
 * until it is recognised; nothing is written anywhere.
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

        /** Louder than this somewhere, or it is taken as silence. */
        private const val SILENCE = 0.02f

        /** The longest dictation at once. */
        const val MAX_SECONDS = 120
        private const val MAX_SAMPLES = MAX_SECONDS * WhisperNative.SAMPLE_RATE
    }

    /** Every use of the model on one thread, so it is never freed while it works. */
    private val executor = Executors.newSingleThreadExecutor()
    private val worker = executor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { handle(it); true })

    /** Loaded on [executor] once, when dictation first starts. */
    private var model: Future<WhisperNative.Model?>? = null
    private var token = 0L
    private var language = "auto"
    private val pieces = ArrayList<ShortArray>()
    private var samples = 0
    private var job: Job? = null

    override fun onBind(intent: Intent): IBinder = messenger.binder

    private fun handle(msg: Message) {
        val data = msg.data
        when (msg.what) {
            START -> {
                cancel()
                token = data.getLong(KEY_TOKEN)
                language = data.getString(KEY_LANGUAGE)?.takeIf { it.matches(Regex("[a-z]{2,3}|auto")) } ?: "auto"
                // Loaded while the person speaks
                if (model == null) {
                    val file = ModelStore(this, PinnedModel.WHISPER).modelFile()
                    model = executor.submit(Callable { file?.let(WhisperNative::load) })
                }
            }
            AUDIO -> if (data.getLong(KEY_TOKEN) == token && job == null) {
                val piece = data.getShortArray(KEY_SAMPLES) ?: return
                // Past the limit the keyboard stops by itself; anything more is not kept
                if (samples + piece.size <= MAX_SAMPLES) {
                    pieces += piece
                    samples += piece.size
                }
            }
            FINISH -> msg.replyTo?.let { if (data.getLong(KEY_TOKEN) == token && job == null) finish(it, token) }
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
            send(reply, FAIL, token) { putString(KEY_REASON, "silence") }
            return
        }
        job = scope.launch {
            try {
                val m = withContext(worker) { model?.get() }
                val text = m?.let { withContext(worker) { it.transcribe(audio, language, threads()) } }
                // A model that could not be loaded is tried again next time
                if (m == null) model = null
                if (text == null) {
                    send(reply, FAIL, token) { putString(KEY_REASON, if (m == null) "model" else "failed") }
                } else {
                    send(reply, TEXT, token) { putString(KEY_TEXT, text) }
                }
            } finally {
                audio.fill(0f)
                if (this@VoiceService.token == token) job = null
            }
        }
    }

    private fun threads() = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)

    private fun clear() {
        for (p in pieces) p.fill(0)
        pieces.clear()
        samples = 0
    }

    private fun cancel() {
        clear()
        if (job != null) WhisperNative.stop()
        job?.cancel()
        job = null
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
        scope.cancel()
        // Freed on the same thread, after anything still running there
        val m = model
        model = null
        executor.execute { runCatching { m?.get()?.close() } }
        executor.shutdown()
        super.onDestroy()
    }
}
