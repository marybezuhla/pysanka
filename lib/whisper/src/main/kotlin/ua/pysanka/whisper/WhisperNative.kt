package ua.pysanka.whisper

import java.io.File

/**
 * Pysanka: whisper.cpp (speech to text) on the phone's processor. One model at a time; [transcribe]
 * blocks (call it off the main thread) and can be stopped with [abort] from another thread.
 * Nothing is logged or written: the audio and the text stay in memory.
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
            "asimddp" in features && "fphp" in features && "asimdhp" in features
        }.getOrDefault(false)
    }

    private val loaded: Boolean by lazy { supported && runCatching { System.loadLibrary("pysanka_whisper") }.isSuccess }

    class Model internal constructor(private var handle: Long) : AutoCloseable {
        /** The text of [audio] (16 kHz mono, -1..1) in [language] ("uk", "en"…); null if it failed or was stopped. */
        fun transcribe(audio: FloatArray, language: String, threads: Int): String? {
            check(handle != 0L) { "Closed" }
            return transcribe(handle, audio, language, threads)?.toString(Charsets.UTF_8)
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

    /** Stops a [Model.transcribe] running now (it then returns null). */
    fun stop() {
        if (loaded) abort()
    }

    private external fun init(path: String): Long
    private external fun transcribe(handle: Long, audio: FloatArray, language: String, threads: Int): ByteArray?
    private external fun abort()
    private external fun free(handle: Long)
}
