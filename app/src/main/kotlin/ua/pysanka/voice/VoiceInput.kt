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
import ua.pysanka.models.ModelStore
import ua.pysanka.models.PinnedModel
import ua.pysanka.privacy.FieldPrivacy
import ua.pysanka.whisper.WhisperNative

/**
 * Pysanka: dictation, the keyboard's side. Records from the microphone (only while the dictation
 * panel is open, 16 kHz mono) and sends the sound in short pieces to [VoiceService], which turns it
 * into text. Nothing is written anywhere; the sound is dropped as soon as it is sent or cancelled.
 * Never in password, incognito or "no learning" fields.
 */
object VoiceInput {
    sealed interface State {
        data object Idle : State
        data class Listening(val seconds: Int, val level: Float) : State
        data object Recognising : State
        data class Failed(@StringRes val message: Int) : State
    }

    enum class Ready { YES, NOT_HERE, NO_MODEL, NO_MIC, UNSUPPORTED }

    val state: StateFlow<State>
        field = MutableStateFlow<State>(State.Idle)

    private val main = Handler(Looper.getMainLooper())
    private var link: Link? = null
    private var recorder: Thread? = null
    @Volatile private var recording = false
    private var token = 0L
    private var onText: ((String) -> Unit)? = null

    fun hasModel(context: Context) = ModelStore(context, PinnedModel.WHISPER).modelFile() != null

    fun hasMic(context: Context) =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Whether dictation can start in the field now, or what is missing. */
    fun ready(context: Context): Ready = when {
        FieldPrivacy.isPassword || FieldPrivacy.isPrivate -> Ready.NOT_HERE
        !WhisperNative.supported -> Ready.UNSUPPORTED
        !hasModel(context) -> Ready.NO_MODEL
        !hasMic(context) -> Ready.NO_MIC
        else -> Ready.YES
    }

    /**
     * Starts listening; the text goes to [onText] (on the main thread). [language]: the keyboard's
     * ("uk", "en"…), or "auto".
     */
    fun start(context: Context, language: String, onText: (String) -> Unit) {
        cancel()
        val app = context.applicationContext
        this.onText = onText
        token++
        val t = token
        link = Link(app) { what, data -> if (data.getLong(VoiceService.KEY_TOKEN) == t) answer(what, data) }.also {
            it.send(VoiceService.START, Bundle().apply {
                putLong(VoiceService.KEY_TOKEN, t)
                putString(VoiceService.KEY_LANGUAGE, language)
            })
        }
        state.value = State.Listening(0, 0f)
        record(t)
    }

    /** Stops listening and recognises what was said. */
    fun finish() {
        if (state.value !is State.Listening) return
        stopRecording()
        state.value = State.Recognising
        link?.send(VoiceService.FINISH, Bundle().apply { putLong(VoiceService.KEY_TOKEN, token) })
    }

    /** Stops everything; nothing is inserted. */
    fun cancel() {
        stopRecording()
        link?.let {
            it.send(VoiceService.CANCEL, Bundle().apply { putLong(VoiceService.KEY_TOKEN, token) })
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
                val text = Transcript.clean(data.getString(VoiceService.KEY_TEXT).orEmpty())
                val deliver = onText
                if (text.isEmpty()) {
                    state.value = State.Failed(R.string.pysanka__voice__nothing)
                } else {
                    state.value = State.Idle
                    deliver?.invoke(text)
                }
                close()
            }
            VoiceService.FAIL -> {
                state.value = State.Failed(
                    when (data.getString(VoiceService.KEY_REASON)) {
                        "silence" -> R.string.pysanka__voice__nothing
                        else -> R.string.pysanka__voice__failed
                    },
                )
                close()
            }
        }
    }

    private fun close() {
        link?.close()
        link = null
        onText = null
    }

    @SuppressLint("MissingPermission") // checked in ready() before the panel opens
    private fun record(t: Long) {
        val rate = WhisperNative.SAMPLE_RATE
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val audio = runCatching {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, rate))
        }.getOrNull()?.takeIf { it.state == AudioRecord.STATE_INITIALIZED }
        if (audio == null) {
            fail(R.string.pysanka__voice__no_mic_now)
            return
        }
        recording = true
        recorder = Thread {
            val piece = ShortArray(rate / 4) // a quarter of a second
            var total = 0
            try {
                audio.startRecording()
                while (recording) {
                    val n = audio.read(piece, 0, piece.size)
                    if (n <= 0) continue
                    val chunk = piece.copyOf(n)
                    total += n
                    var peak = 0
                    for (s in chunk) peak = maxOf(peak, kotlin.math.abs(s.toInt()))
                    val seconds = total / rate
                    main.post {
                        if (token != t || !recording) return@post
                        link?.send(VoiceService.AUDIO, Bundle().apply {
                            putLong(VoiceService.KEY_TOKEN, t)
                            putShortArray(VoiceService.KEY_SAMPLES, chunk)
                        })
                        state.value = State.Listening(seconds, peak / 32768f)
                        // The longest dictation: recognised as it is
                        if (seconds >= VoiceService.MAX_SECONDS) finish()
                    }
                }
            } catch (_: Throwable) {
                main.post { if (token == t) fail(R.string.pysanka__voice__no_mic_now) }
            } finally {
                piece.fill(0)
                runCatching { audio.stop() }
                audio.release()
            }
        }.apply { name = "pysanka-voice"; start() }
    }

    private fun fail(@StringRes message: Int) {
        stopRecording()
        link?.send(VoiceService.CANCEL, Bundle().apply { putLong(VoiceService.KEY_TOKEN, token) })
        close()
        state.value = State.Failed(message)
    }

    private fun stopRecording() {
        recording = false
        recorder = null
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
            if (!bound) main.post { if (link === this) fail(R.string.pysanka__voice__failed) }
        }

        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = binder?.let(::Messenger)
            waiting.forEach(::deliver)
            waiting.clear()
        }

        // The recognition's process ended (out of memory, say)
        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            if (link === this && state.value !is State.Idle) fail(R.string.pysanka__voice__failed)
        }

        fun send(what: Int, data: Bundle) {
            val msg = Message.obtain(null, what).apply {
                this.data = data
                replyTo = incoming
            }
            val s = service
            if (s == null) waiting += msg else deliver(msg)
        }

        private fun deliver(msg: Message) {
            if (runCatching { service?.send(msg) }.isFailure && link === this) fail(R.string.pysanka__voice__failed)
        }

        fun close() {
            waiting.clear()
            if (bound) runCatching { app.unbindService(this) }
        }
    }
}
