package ua.pysanka.whisper

import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Pysanka: whisper.cpp (speech to text) on the phone's processor. [Model.transcribe] blocks (call it
 * off the main thread) and can be stopped with [stop] from another thread. Nothing is logged or
 * written: the audio and the text stay in memory.
 */
object WhisperNative {
    /** 16 kHz mono, as whisper expects. */
    const val SAMPLE_RATE = 16_000

    /**
     * Whether this phone's processor can run the library (it is built for ARMv8.2 with half floats
     * and dot product, as nearly every phone since 2018); checked before it is loaded.
     */
    val supported: Boolean by lazy {
        runCatching {
            val features = File("/proc/cpuinfo").readLines().firstOrNull { it.startsWith("Features") }.orEmpty()
                .substringAfter(':').trim().split(' ').toSet()
            features.containsAll(listOf("asimddp", "fphp", "asimdhp", "atomics", "asimdrdm"))
        }.getOrDefault(false)
    }

    private val loaded: Boolean by lazy {
        supported && runCatching {
            // Before the library starts: on a fatal error ggml would fork to print a backtrace
            android.system.Os.setenv("GGML_NO_BACKTRACE", "1", true)
            System.loadLibrary("pysanka_whisper")
        }.isSuccess
    }

    private val runs = AtomicLong(0)

    /** A number for a run that is about to start, to [stop] it by. */
    fun newRun(): Long = runs.incrementAndGet()

    class Model internal constructor(private var handle: Long) : AutoCloseable {
        /**
         * The text of [audio] (16 kHz mono, -1..1) in [language] ("uk", "en"…); null if it failed or
         * [run] was stopped.
         */
        fun transcribe(audio: FloatArray, language: String, threads: Int, run: Long): String? {
            check(handle != 0L) { "Closed" }
            return transcribe(handle, audio, language, threads, run)?.toString(Charsets.UTF_8)
        }

        override fun close() {
            if (handle != 0L) free(handle)
            handle = 0L
        }
    }

    /** The model in [file], or null if it cannot be read (or this phone cannot run it). */
    fun load(file: File): Model? {
        if (!loaded) return null
        val h = init(file.absolutePath)
        return if (h == 0L) null else Model(h)
    }

    /** Stops [run] (and any earlier one), whether it runs now or has not started yet. */
    fun stop(run: Long) {
        if (loaded) nativeStop(run)
    }

    private external fun init(path: String): Long
    private external fun transcribe(handle: Long, audio: FloatArray, language: String, threads: Int, gen: Long): ByteArray?
    private external fun nativeStop(gen: Long)
    private external fun free(handle: Long)
}
