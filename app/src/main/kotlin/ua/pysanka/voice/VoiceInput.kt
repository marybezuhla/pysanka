package ua.pysanka.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import androidx.annotation.StringRes
import dev.patrickgold.florisboard.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ua.pysanka.privacy.FieldPrivacy
import ua.pysanka.whisper.WhisperNative

/**
 * Pysanka: dictation, the keyboard's side. Records from the microphone (only while the dictation
 * panel is open, 16 kHz mono) and sends the sound in short pieces to [VoiceService], which turns it
 * into text. Nothing is written anywhere; each piece is overwritten once it is sent. Never in
 * password, incognito or "no learning" fields.
 */
object VoiceInput {
    sealed interface State {
        data object Idle : State
        /** [seconds] listened so far (of [VoiceService.MAX_SECONDS]); [heard]: speech has begun. */
        data class Listening(val seconds: Float, val level: Float, val heard: Boolean = false) : State
        data object Recognising : State
        data class Failed(@StringRes val message: Int) : State
    }

    enum class Ready { YES, NOT_HERE, NO_MIC, NO_MODEL, UNSUPPORTED }

    val state: StateFlow<State>
        field = MutableStateFlow<State>(State.Idle)

    /**
     * The text the last dictation put in the field and where the cursor was after it (to take it
     * back), until the panel changes the text or closes.
     */
    data class Inserted(val text: String, val end: Int)
    val inserted = MutableStateFlow<Inserted?>(null)

    private val main = Handler(Looper.getMainLooper())
    private var link: Link? = null
    private var recorder: Thread? = null

    /** One recording's flags (a recorder that has not ended yet never sees the next one's). */
    private class Rec {
        /** Stop recording: the recorder sends what it still has, then asks for the text. */
        @Volatile var stopping = false
        /** Drop the recording: the recorder ends without sending more. */
        @Volatile var dropped = false
    }
    private var rec = Rec()
    private var token = 0L
    private var onText: ((String) -> Unit)? = null
    private var language = ""

    fun hasMic(context: Context) =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Whether dictation may work in the field now. */
    fun allowedHere() = !FieldPrivacy.isPassword && !FieldPrivacy.isPrivate

    /** Whether dictation can start in the field now, or what is missing. */
    fun ready(context: Context): Ready = when {
        !allowedHere() -> Ready.NOT_HERE
        !WhisperNative.supported -> Ready.UNSUPPORTED
        !VoiceModel.present(context) -> Ready.NO_MODEL
        !hasMic(context) -> Ready.NO_MIC
        else -> Ready.YES
    }

    /**
     * Starts listening; the text goes to [onText] (on the main thread). [language]: the keyboard's
     * ("uk", "en"…), or "auto".
     */
    fun start(context: Context, language: String, onText: (String) -> Unit) {
        cancel()
        // The previous recorder lets the microphone go before a new one takes it
        recorder?.join(500)
        val app = context.applicationContext
        this.onText = onText
        this.language = language
        token++
        val t = token
        link = Link(app) { what, data -> if (data.getLong(VoiceService.KEY_TOKEN) == t) answer(what, data) }.also {
            it.send(VoiceService.START, t) { putString(VoiceService.KEY_LANGUAGE, language) }
        }
        state.value = State.Listening(0f, 0f)
        val prefs by dev.patrickgold.florisboard.app.FlorisPreferenceStore
        // Held to speak: the release ends it, never a pause
        record(t, runCatching { prefs.pysankaVoice.autoStop.get() && !prefs.pysankaVoice.holdToTalk.get() }.getOrDefault(true))
    }

    /** Stops listening and recognises what was said. */
    fun finish() {
        if (state.value !is State.Listening) return
        state.value = State.Recognising
        // The recorder sends the end of what was said, then FINISH, in that order
        rec.stopping = true
    }

    /** Stops everything; nothing is inserted. */
    fun cancel() {
        rec.dropped = true
        link?.let {
            it.send(VoiceService.CANCEL, token)
            it.close()
        }
        link = null
        onText = null
        token++
        state.value = State.Idle
    }

    private fun answer(what: Int, data: Bundle) {
        if (state.value !is State.Recognising) return
        when (what) {
            VoiceService.TEXT -> {
                val text = Transcript.clean(data.getString(VoiceService.KEY_TEXT).orEmpty(), language)
                val deliver = onText
                close()
                if (text.isEmpty()) {
                    state.value = State.Failed(R.string.pysanka__voice__nothing)
                } else {
                    state.value = State.Idle
                    deliver?.invoke(text)
                }
            }
            VoiceService.FAIL -> {
                close()
                state.value = State.Failed(
                    if (data.getString(VoiceService.KEY_REASON) == "silence") R.string.pysanka__voice__nothing else R.string.pysanka__voice__failed,
                )
            }
        }
    }

    private fun close() {
        link?.close()
        link = null
        onText = null
    }

    /** Louder than this (of 32768) is speech; quieter than [QUIET], a pause. */
    private const val SPEECH = 1200
    private const val QUIET = 700
    private const val PAUSE_SECONDS = 2

    @SuppressLint("MissingPermission") // checked in ready() before the panel opens
    private fun record(t: Long, autoStop: Boolean) {
        val rate = WhisperNative.SAMPLE_RATE
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val audio = runCatching {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, rate))
        }.getOrNull()
        if (audio == null || audio.state != AudioRecord.STATE_INITIALIZED) {
            audio?.release()
            fail(t, R.string.pysanka__voice__no_mic_now)
            return
        }
        val r = Rec()
        rec = r
        recorder = Thread {
            val piece = ShortArray(rate / 4) // a quarter of a second
            var elapsed = 0
            // Silence before the first words is not sent (Whisper makes words up for it); the last
            // quarter second before them is, so the first sound is not cut
            var heard = false
            var before: ShortArray? = null
            var quiet = 0
            fun peakOf(a: ShortArray, n: Int): Int {
                var p = 0
                for (k in 0 until n) p = maxOf(p, kotlin.math.abs(a[k].toInt()))
                return p
            }
            fun ship(chunk: ShortArray) = main.post {
                var sent = false
                if (token == t && !r.dropped) sent = link?.send(VoiceService.AUDIO, t) { putShortArray(VoiceService.KEY_SAMPLES, chunk) } == true
                // Sent (copied to the other process) or not wanted: not kept here; one still
                // waiting for the connection is cleared once it is sent
                if (sent || token != t || r.dropped) chunk.fill(0)
            }
            fun send(n: Int) {
                val peak = peakOf(piece, n)
                elapsed += n
                if (!heard && peak >= SPEECH) {
                    heard = true
                    before?.let(::ship)
                    before = null
                }
                if (heard) ship(piece.copyOf(n)) else before = piece.copyOf(n).also { before?.fill(0) }
                // A pause after the words: done, as if «Готово» was tapped (if chosen)
                quiet = if (heard && peak < QUIET) quiet + n else 0
                val pause = autoStop && quiet >= rate * PAUSE_SECONDS
                val seconds = elapsed.toFloat() / rate
                main.post {
                    if (token == t && !r.dropped && state.value is State.Listening) {
                        state.value = State.Listening(seconds, peak / 32768f, heard)
                        // The longest dictation: recognised as it is
                        if (seconds >= VoiceService.MAX_SECONDS || pause) finish()
                    }
                }
            }
            try {
                audio.startRecording()
                check(audio.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                while (!r.stopping && !r.dropped) {
                    val n = audio.read(piece, 0, piece.size)
                    check(n >= 0) // the microphone failed (another app took it, the audio system restarted)
                    if (n > 0) send(n)
                }
                if (!r.dropped) {
                    // What was said just before «Готово», still in the microphone's buffer
                    audio.stop()
                    while (true) {
                        val n = audio.read(piece, 0, piece.size)
                        if (n <= 0) break
                        send(n)
                    }
                    main.post { if (token == t && !r.dropped) link?.send(VoiceService.FINISH, t) }
                }
            } catch (_: Throwable) {
                main.post { if (token == t && !r.dropped) fail(t, R.string.pysanka__voice__no_mic_now) }
            } finally {
                piece.fill(0)
                before?.fill(0)
                runCatching { audio.stop() }
                audio.release()
            }
        }.apply { name = "pysanka-voice"; start() }
    }

    private fun fail(t: Long, @StringRes message: Int) {
        if (token != t) return
        rec.dropped = true
        link?.send(VoiceService.CANCEL, t)
        close()
        state.value = State.Failed(message)
    }

    /** The connection to [VoiceService]; messages sent before it connects are delivered in order. */
    private class Link(context: Context, private val onMessage: (Int, Bundle) -> Unit) : ServiceConnection {
        private val app = context.applicationContext
        private val incoming = Messenger(Handler(Looper.getMainLooper()) { msg -> onMessage(msg.what, msg.data); true })
        private var service: Messenger? = null
        private val waiting = ArrayList<Message>()
        private val bound = runCatching { app.bindService(Intent(app, VoiceService::class.java), this, Context.BIND_AUTO_CREATE) }
            .getOrDefault(false)

        init {
            if (!bound) main.post { if (link === this) fail(token, R.string.pysanka__voice__failed) }
        }

        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = binder?.let(::Messenger)
            waiting.forEach(::deliver)
            waiting.clear()
        }

        // The recognition's process ended (out of memory, say)
        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            if (link === this && state.value !is State.Idle) fail(token, R.string.pysanka__voice__failed)
        }

        /** A message for the run [t], filled with [fill]; true if it went at once (not waiting for the connection). */
        fun send(what: Int, t: Long, fill: Bundle.() -> Unit = {}): Boolean {
            val msg = Message.obtain(null, what).apply {
                data = Bundle().apply {
                    putLong(VoiceService.KEY_TOKEN, t)
                    fill()
                }
                replyTo = incoming
            }
            if (service == null) {
                waiting += msg
                return false
            }
            return deliver(msg)
        }

        private fun deliver(msg: Message): Boolean {
            val ok = runCatching { service?.send(msg) }.isSuccess
            if (!ok && link === this) main.post { fail(token, R.string.pysanka__voice__failed) }
            // Copied to the other process: what was sent is not kept here
            msg.data?.getShortArray(VoiceService.KEY_SAMPLES)?.fill(0)
            return ok
        }

        fun close() {
            waiting.clear()
            if (bound) runCatching { app.unbindService(this) }
        }
    }
}
