package ua.pysanka.speech

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Pysanka as the phone's voice (optional: turned on in Pysanka's settings, then chosen in Android's
 * text-to-speech settings): Ukrainian with Tetiana, English with Cori, the voices that are
 * downloaded. Other apps (and screen readers) hand it text; it is made into sound in this process
 * and never written anywhere. Other languages are not offered.
 */
class PysankaTtsService : TextToSpeechService() {
    @Volatile private var current: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var idle: Job? = null
    @Volatile private var language = arrayOf("ukr", "", "")

    private fun available(lang: String?): Int {
        val code = when (lang?.lowercase()) { "ukr", "uk" -> "uk"; "eng", "en" -> "en"; else -> return TextToSpeech.LANG_NOT_SUPPORTED }
        return if (SpeechEngine.has(this, code)) TextToSpeech.LANG_AVAILABLE else TextToSpeech.LANG_MISSING_DATA
    }

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int = available(lang)

    override fun onGetLanguage(): Array<String> = language

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        val result = available(lang)
        if (result >= TextToSpeech.LANG_AVAILABLE) language = arrayOf(if (lang?.lowercase()?.startsWith("en") == true) "eng" else "ukr", "", "")
        return result
    }

    override fun onStop() {
        current?.cancel()
    }

    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        val text = request.charSequenceText?.toString() ?: request.text.orEmpty()
        val lang = request.language
        if (available(lang) < TextToSpeech.LANG_AVAILABLE) { callback.error(TextToSpeech.ERROR_NOT_INSTALLED_YET); return }
        // Android's rate: 100 is normal
        val speed = (request.speechRate / 100f).coerceIn(0.5f, 2.5f)
        // The job exists before anything starts, so a stop that comes at once is not lost
        val job = Job().also { current = it }
        idle?.cancel()
        try {
            if (callback.start(TetianaVoice.RATE, AudioFormat.ENCODING_PCM_16BIT, 1) != TextToSpeech.SUCCESS) return
            runBlocking(job) {
                // The text says which language each sentence is in; a request for English reads English
                // sentences with Cori and Ukrainian ones with Tetiana, whichever is there
                for (span in Sentences.split(text)) {
                    val sentence = text.substring(span.start, span.end)
                    val sound = SpeechEngine.make(this@PysankaTtsService, sentence, speed) ?: continue
                    if (!send(callback, ReadAloud.shaped(sound, TetianaVoice.RATE))) return@runBlocking
                    if (!send(callback, FloatArray((TetianaVoice.RATE * 0.25f).toInt()))) return@runBlocking
                }
                callback.done()
            }
        } catch (_: kotlinx.coroutines.CancellationException) {
            // Stopped (TalkBack does it all the time): Android knows; nothing else to do
        } finally {
            current = null
            job.cancel()
            // The voices are freed after a few quiet minutes (a screen reader asks again soon)
            idle = scope.launch { delay(IDLE_MS); if (!ReadAloud.state.value.active) SpeechEngine.release() }
        }
    }

    override fun onDestroy() {
        current?.cancel()
        idle?.cancel()
        // Not on the main thread: a sentence still being made is waited for elsewhere
        CoroutineScope(Dispatchers.Default).launch { if (!ReadAloud.state.value.active) SpeechEngine.release() }
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val IDLE_MS = 3 * 60_000L
    }

    /** Sound handed to Android as 16-bit samples, in pieces it takes; false when it wants no more. */
    private fun send(callback: SynthesisCallback, samples: FloatArray): Boolean {
        val max = callback.maxBufferSize.coerceAtLeast(2)
        val bytes = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val v = (samples[i].coerceIn(-1f, 1f) * 32767f).toInt()
            bytes[2 * i] = (v and 0xFF).toByte()
            bytes[2 * i + 1] = (v shr 8 and 0xFF).toByte()
        }
        var off = 0
        while (off < bytes.size) {
            val n = minOf(max - max % 2, bytes.size - off)
            if (callback.audioAvailable(bytes, off, n) != TextToSpeech.SUCCESS) return false
            off += n
        }
        return true
    }
}
