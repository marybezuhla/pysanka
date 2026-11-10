package ua.pysanka.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Pysanka: the voices in the ":speech" process (reading aloud, the word in a translation, Pysanka as
 * the phone's voice): opened once, used by one sentence at a time on their own thread, freed when
 * nothing needs them. A sentence is said by the voice for its language (Tetiana for Ukrainian, Cori
 * for English); a voice that is not downloaded says nothing.
 */
object SpeechEngine {
    private val lock = Mutex()
    private val synth = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "pysanka-voice") }.asCoroutineDispatcher()
    private var tetiana: TetianaVoice? = null
    private var tetianaFailed = false
    private var english: EnglishVoice? = null
    private var englishFailed = false

    /** Whether there is a voice for "uk" or "en" on this phone (downloaded). */
    fun has(context: Context, language: String): Boolean = when (language) {
        "uk" -> ua.pysanka.models.ModelStore(context, ua.pysanka.models.PinnedModel.TETIANA).modelFile() != null
        "en" -> ua.pysanka.models.ModelStore(context, ua.pysanka.models.PinnedModel.CORI).modelFile() != null
        else -> false
    }

    /**
     * [text] (one sentence) made into sound at [speed], or null when it cannot be said. [onOpening] is
     * called when a voice is being opened (the first time takes a moment).
     */
    suspend fun make(context: Context, text: String, speed: Float, onOpening: () -> Unit = {}): Sound? {
        val app = context.applicationContext
        return when (Sentences.language(text)) {
            "uk" -> lock.withLock {
                if (tetiana == null && !tetianaFailed && has(app, "uk")) {
                    onOpening()
                    tetiana = withContext(synth) { try { TetianaVoice.open(app, TetianaVoice.dictionary(app)) } catch (e: Exception) { null } catch (e: OutOfMemoryError) { null } }
                    if (tetiana == null) tetianaFailed = true
                }
                // The model runs under the lock: it is never closed while a sentence is being made
                tetiana?.let { v -> try { withContext(synth) { v.read(text, speed) } } catch (e: CancellationException) { throw e } catch (e: Exception) { null } catch (e: OutOfMemoryError) { null } }
            }
            "en" -> lock.withLock {
                if (english == null && !englishFailed && has(app, "en")) {
                    onOpening()
                    english = withContext(synth) { try { EnglishVoice.open(app) } catch (e: Exception) { null } catch (e: UnsatisfiedLinkError) { null } catch (e: OutOfMemoryError) { null } }
                    if (english == null) englishFailed = true
                }
                english?.let { v -> try { withContext(synth) { v.read(text, speed) } } catch (e: CancellationException) { throw e } catch (e: Exception) { null } catch (e: OutOfMemoryError) { null } }
            }
            else -> null
        }
    }

    /** Frees the voices (memory); they open again when needed. Waits for a sentence being made. */
    suspend fun release() = lock.withLock {
        tetiana?.close(); tetiana = null; tetianaFailed = false
        english?.close(); english = null; englishFailed = false
    }
}

/**
 * Pysanka: reading a text aloud (from the selection menu, or a word from the translation), sentence
 * by sentence while the next one is already being made. Every control runs on the main thread; one
 * reading is one job with its own sound output, so stopping or skipping never touches another's.
 */
object ReadAloud {
    data class State(
        val text: String = "",
        val playing: Boolean = false,
        /** Getting a voice ready (the first time it takes a moment). */
        val preparing: Boolean = false,
        val start: Int = 0,
        val end: Int = 0,
        val speed: Float = 1f,
        /** Reading goes on without the window (the notification has the controls). */
        val background: Boolean = false,
        /** Said when something could not be read. */
        val notice: Int? = null,
    ) { val active get() = text.isNotEmpty() }

    const val NOTICE_NO_UK = 1
    const val NOTICE_NO_EN = 2
    const val NOTICE_NO_LANGUAGE = 3
    const val NOTICE_NO_SOUND = 4
    /** Paused this long, reading ends (the controls go and the voices' memory is freed). */
    private const val PAUSED_LIMIT = 30 * 60_000L
    /** The most read from one selection. */
    const val MAX_CHARS = 100_000

    // A failure in the sound system must never close Pysanka: reading just stops, with a word why
    private val guard = CoroutineExceptionHandler { _, _ ->
        _state.update { it.copy(playing = false, preparing = false, notice = NOTICE_NO_SOUND) }
        main.launch { pause() }
    }
    private val main: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + guard)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var job: Job? = null
    private var idleJob: Job? = null
    private var spans: List<Sentences.Span> = emptyList()
    private lateinit var app: Context

    /** Starts reading [text] from its beginning. */
    fun start(context: Context, text: String, speed: Float, background: Boolean): Unit = onMain {
        app = context.applicationContext
        job?.cancel()
        idleJob?.cancel()
        val t = text.take(MAX_CHARS)
        spans = Sentences.split(t)
        val first = spans.firstOrNull()
        _state.value = State(t, playing = true, start = first?.start ?: 0, end = first?.end ?: 0, speed = speed, background = background)
        if (background) ReadAloudService.show(app)
        run(0, 0)
    }

    fun pause(): Unit = onMain {
        job?.cancel(); job = null
        _state.update { it.copy(playing = false, preparing = false) }
        idleJob?.cancel(); idleJob = main.launch { delay(PAUSED_LIMIT); stopNow() }
    }

    fun resume(): Unit = onMain {
        val s = _state.value
        if (!s.active || s.playing) return@onMain
        idleJob?.cancel()
        _state.update { it.copy(playing = true, notice = null) }
        if (s.background && ::app.isInitialized) ReadAloudService.show(app)
        run(index(s.start), 0)
    }

    fun toggle(): Unit = onMain { if (_state.value.playing) pause() else resume() }

    /** To the next or the previous sentence (while paused, only the place moves). */
    fun skip(by: Int): Unit = onMain {
        val s = _state.value
        if (!s.active) return@onMain
        run(index(s.start), by)
    }

    fun setSpeed(speed: Float): Unit = onMain {
        _state.update { it.copy(speed = speed) }
        val s = _state.value
        if (s.playing) run(index(s.start), 0)
    }

    /** Ends reading (✕, or the window closed without reading in the background). */
    fun stop(): Unit = onMain { stopNow() }

    fun clearNotice() = _state.update { it.copy(notice = null) }

    /** [block] on the main thread once nothing is being read (paused, ended or stopped). */
    fun whenIdle(block: () -> Unit) {
        main.launch { _state.first { !it.playing }; block() }
    }

    private fun stopNow() {
        job?.cancel(); job = null
        idleJob?.cancel(); idleJob = null
        spans = emptyList()
        _state.value = State(speed = _state.value.speed)
        if (::app.isInitialized) ReadAloudService.hide(app)
        main.launch(Dispatchers.Default) { if (!_state.value.active) SpeechEngine.release() }
    }

    private fun onMain(block: () -> Unit) { main.launch { block() } }

    private fun index(at: Int) = spans.indexOfLast { it.start <= at }.coerceAtLeast(0)

    /** One reading: moves [by] sentences from sentence [k] first, then (when playing) reads on from there. */
    private fun run(k: Int, by: Int) {
        job?.cancel()
        val all = spans
        if (all.isEmpty()) return
        val from = (k + by).coerceIn(0, all.size - 1)
        _state.update { it.copy(start = all[from].start, end = all[from].end) }
        if (!_state.value.playing) return
        job = main.launch(Dispatchers.Default) { read(all, from) }
    }

    private suspend fun read(all: List<Sentences.Span>, from: Int) = kotlinx.coroutines.coroutineScope {
        val text = _state.value.text
        val made = Channel<Pair<Int, Sound?>>(capacity = 2)
        // Made ahead: the next sentence is ready when this one ends
        val maker = launch {
            for (k in from until all.size) {
                val sentence = text.substring(all[k].start, all[k].end)
                val sound = SpeechEngine.make(app, sentence, _state.value.speed) { _state.update { it.copy(preparing = true) } }
                made.send(k to sound)
            }
            made.close()
        }
        val out = Output()
        try {
            while (isActive) {
                // While the next sentence is still being made, quiet sound keeps playing: the pause is
                // heard as a pause and the phone stays awake with the screen off
                var next = made.tryReceive()
                while (next.isFailure && !next.isClosed && isActive) { out.silence(0.1f); next = made.tryReceive() }
                val (k, sound) = next.getOrNull() ?: break
                _state.update { it.copy(start = all[k].start, end = all[k].end, preparing = false) }
                if (sound == null) {
                    // No voice for this sentence's language: reading stops here (the place kept), saying why
                    val lang = Sentences.language(text.substring(all[k].start, all[k].end))
                    if (lang != null && !SpeechEngine.has(app, lang)) {
                        _state.update { it.copy(notice = if (lang == "uk") NOTICE_NO_UK else NOTICE_NO_EN) }
                        withContext(Dispatchers.Main) { pause() }
                        return@coroutineScope
                    }
                    continue
                }
                out.play(sound)
                val paragraphEnd = all[k].end >= text.length || text.getOrNull(all[k].end) == '\n'
                out.silence(if (paragraphEnd) 0.55f else 0.28f)
            }
            out.drain()
            // The end: back to the beginning, paused
            if (isActive) {
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(start = all.first().start, end = all.first().end) }
                    pause()
                }
            }
        } finally {
            maker.cancel()
            out.release()
        }
    }

    /** Sound output of one reading: its own track at the voices' rate (other rates resampled), released at the end. */
    internal class Output {
        private val rate = TetianaVoice.RATE
        private var track: AudioTrack? = null
        private var written = 0L

        private fun track(): AudioTrack = track ?: run {
            val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            val format = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
            AudioTrack.Builder().setAudioAttributes(attrs).setAudioFormat(format).setBufferSizeInBytes(maxOf(min, rate / 2 * 4))
                .setTransferMode(AudioTrack.MODE_STREAM).build().also { track = it; it.play() }
        }

        private fun head(t: AudioTrack) = t.playbackHeadPosition.toLong() and 0xFFFFFFFFL

        suspend fun play(sound: Sound) {
            val all = shaped(sound, rate)
            write(all)
            // Shown as it is heard: wait until most of this sentence has been played, not just handed over
            val t = track ?: return
            val target = written - rate / 10
            val deadline = android.os.SystemClock.uptimeMillis() + all.size * 1000L / rate + 2000
            while (kotlinx.coroutines.currentCoroutineContext().isActive && head(t) < target && android.os.SystemClock.uptimeMillis() < deadline) delay(15)
        }

        suspend fun silence(seconds: Float) = write(FloatArray((rate * seconds).toInt()))

        private suspend fun write(data: FloatArray) = withContext(Dispatchers.IO) {
            val t = track()
            var off = 0
            while (off < data.size && isActive) {
                val n = t.write(data, off, minOf(2048, data.size - off), AudioTrack.WRITE_BLOCKING)
                // The sound system lost the track (restarted, the output changed): reading stops with a word why
                if (n < 0) throw IllegalStateException("audio")
                if (n == 0) break
                off += n
            }
            written += off
        }

        /** The end of a reading: what is left is played out, within a time. */
        suspend fun drain() {
            val t = track ?: return
            val left = written - head(t)
            try { t.stop() } catch (_: Exception) { return }
            val deadline = android.os.SystemClock.uptimeMillis() + left.coerceAtLeast(0) * 1000 / rate + 500
            while (kotlinx.coroutines.currentCoroutineContext().isActive && t.playState != AudioTrack.PLAYSTATE_STOPPED && android.os.SystemClock.uptimeMillis() < deadline) delay(20)
            while (kotlinx.coroutines.currentCoroutineContext().isActive && head(t) < written && android.os.SystemClock.uptimeMillis() < deadline) delay(20)
        }

        fun release() { track?.let { try { it.pause(); it.flush(); it.release() } catch (_: Exception) {} }; track = null }
    }

    /** A sentence at [rate], with a gentle edge (no click where it starts and ends). */
    internal fun shaped(sound: Sound, rate: Int): FloatArray {
        val s = if (sound.rate == rate) sound.samples else resample(sound.samples, sound.rate, rate)
        val fade = minOf(220, s.size / 4)
        return FloatArray(s.size) { i ->
            val g = when { i < fade -> i / fade.toFloat(); i >= s.size - fade -> (s.size - i) / fade.toFloat(); else -> 1f }
            (s[i] * g * 0.95f).coerceIn(-1f, 1f)
        }
    }

    private fun resample(x: FloatArray, from: Int, to: Int): FloatArray {
        val n = (x.size.toLong() * to / from).toInt()
        return FloatArray(n) { i ->
            val p = i.toDouble() * from / to; val a = p.toInt(); val f = (p - a).toFloat()
            val v0 = x.getOrElse(a) { 0f }; val v1 = x.getOrElse(a + 1) { v0 }
            v0 + (v1 - v0) * f
        }
    }
}
