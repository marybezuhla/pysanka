package ua.pysanka.speech

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.nio.channels.FileChannel

/*
 * Pysanka: the voices of Hramota (the same models, text preparation and pace), read on the phone.
 * The two models are downloaded by the person (see [ua.pysanka.models.PinnedModel.TETIANA] and
 * [ua.pysanka.models.PinnedModel.CORI]); the small parts (stress dictionary, Tetiana's tokens and
 * speaker, Cori's settings, eSpeak's English data) come with the app.
 */

/** Sound for one sentence: samples between -1 and 1 at [rate]. */
class Sound(val samples: FloatArray, val rate: Int)

/** A voice that reads a sentence into sound. */
interface Voice {
    suspend fun read(text: String, speed: Float): Sound?
    fun close() {}
}

/** Files that come with the app (not compressed in the APK, so they are mapped, not copied). */
internal fun mapAsset(context: Context, name: String): ByteBuffer {
    val fd = context.assets.openFd(name)
    return FileInputStream(fd.fileDescriptor).use { it.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength) }
}

/** A downloaded model, mapped (it is only there when it is exactly the pinned file). */
internal fun mapModel(context: Context, model: ua.pysanka.models.PinnedModel): ByteBuffer {
    val file = ua.pysanka.models.ModelStore(context, model).modelFile() ?: throw IllegalStateException("no ${model.name}")
    return RandomAccessFile(file, "r").use { raf -> raf.channel.use { it.map(FileChannel.MapMode.READ_ONLY, 0, it.size()) } }
}

/**
 * Pysanka's Ukrainian voice: Tetiana (robinhad/ukrainian-tts v5, MIT; recordings Apache-2.0), a VITS model run on
 * the phone. Text gets its stresses from the dictionary first ([UkrainianText]); the voice was trained on stressed text.
 */
class TetianaVoice private constructor(private val session: OrtSession, private val env: OrtEnvironment, private val tokens: Map<Char, Long>, private val speaker: FloatArray, val text: UkrainianText) : Voice {
    private val space = tokens[' '] ?: 3L

    /** The letters and marks the voice knows, as its token numbers; anything else is left out. */
    fun ids(prepared: String): LongArray {
        val out = ArrayList<Long>(prepared.length)
        var lastSpace = true
        for (raw in prepared.lowercase()) {
            val c = when (raw) {
                '«', '»', '“', '”' -> '"'; '—', '–', '−' -> '-'; '…' -> '.'; ';' -> ','; '’', 'ʼ', '`' -> '\''
                '\n', '\t', ' ' -> ' '
                else -> raw
            }
            if (c == ' ') { if (!lastSpace) out += space; lastSpace = true; continue }
            val id = tokens[c] ?: continue
            out += id; lastSpace = false
        }
        while (out.isNotEmpty() && out.last() == space) out.removeAt(out.size - 1)
        return out.toLongArray()
    }

    override suspend fun read(text: String, speed: Float): Sound? {
        val prepared = this.text.prepare(text)
        // Numbers written out can make a sentence long: read in pieces of a sensible size, joined
        val pieces = ArrayList<String>()
        var rest = prepared
        while (rest.length > PIECE) {
            val cut = rest.lastIndexOf(' ', PIECE).takeIf { it > PIECE / 3 } ?: PIECE
            pieces += rest.substring(0, cut); rest = rest.substring(cut).trimStart()
        }
        if (rest.isNotBlank()) pieces += rest
        val parts = pieces.mapNotNull { piece -> ids(piece).takeIf { it.isNotEmpty() }?.let { run(it, speed) } }
        if (parts.isEmpty()) return null
        return Sound(if (parts.size == 1) parts[0] else FloatArray(parts.sumOf { it.size }).also { out -> var at = 0; for (p in parts) { p.copyInto(out, at); at += p.size } }, RATE)
    }

    /** One run of the model; stopped at once (not after the whole sentence) when reading moves on. */
    private suspend fun run(ids: LongArray, speed: Float): FloatArray? = kotlinx.coroutines.coroutineScope {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        // Longer sounds are slower: the voice's own pace times the person's speed
        val scales = floatArrayOf(0.667f, BASE_LENGTH / speed.coerceIn(0.5f, 2.5f), 0.8f)
        val options = OrtSession.RunOptions()
        // (on another thread: the voice's own thread is busy running the model)
        val watcher = launch(kotlinx.coroutines.Dispatchers.Default) { try { kotlinx.coroutines.awaitCancellation() } finally { try { options.setTerminate(true) } catch (_: Exception) {} } }
        try {
            OnnxTensor.createTensor(env, LongBuffer.wrap(ids), longArrayOf(1, ids.size.toLong())).use { t ->
                OnnxTensor.createTensor(env, FloatBuffer.wrap(speaker), longArrayOf(speaker.size.toLong())).use { s ->
                    OnnxTensor.createTensor(env, FloatBuffer.wrap(scales), longArrayOf(3)).use { sc ->
                        // The sentence's length too, when the model asks for it
                        val lengths = if ("text_lengths" in session.inputNames) OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(ids.size.toLong())), longArrayOf(1)) else null
                        val inputs = buildMap { put("text", t); put("spembs", s); put("scales", sc); lengths?.let { put("text_lengths", it) } }
                        try {
                            session.run(inputs, options).use { r ->
                                @Suppress("UNCHECKED_CAST")
                                (r[0].value as Array<FloatArray>)[0]
                            }
                        } finally { lengths?.close() }
                    }
                }
            }
        } finally {
            // The watcher is done before the options it touches are closed
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { watcher.cancelAndJoin() }
            options.close()
        }
    }

    override fun close() { try { session.close() } catch (_: Exception) {} }

    companion object {
        const val RATE = 22050
        /** The voice's own pace stretched to a calm narrator's, about four syllables a second. */
        const val BASE_LENGTH = 1.0f
        /** Longest piece read in one run (letters): longer sentences are read in pieces. */
        private const val PIECE = 400

        fun open(context: Context, dictionary: StressDictionary?): TetianaVoice {
            val env = OrtEnvironment.getEnvironment()
            val session = OrtSession.SessionOptions().use { options ->
                // The big cores of a phone; the rest of the app stays quick
                options.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
                options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                // Memory given back after each sentence instead of kept for the longest one
                options.setCPUArenaAllocator(false)
                env.createSession(mapModel(context, ua.pysanka.models.PinnedModel.TETIANA), options)
            }
            try { return finishOpen(context, session, env, dictionary) } catch (e: Throwable) { session.close(); throw e }
        }

        private fun finishOpen(context: Context, session: OrtSession, env: OrtEnvironment, dictionary: StressDictionary?): TetianaVoice {
            val lines = context.assets.open("speech/uk-tetiana.tokens.txt").bufferedReader().readLines()
            val tokens = HashMap<Char, Long>()
            lines.forEachIndexed { i, t -> when { t == "<space>" -> tokens[' '] = i.toLong(); t.length == 1 -> tokens[t[0]] = i.toLong() } }
            val bytes = context.assets.open("speech/uk-tetiana.xvector.bin").use { it.readBytes() }
            val speaker = FloatArray(bytes.size / 4).also { ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(it) }
            return TetianaVoice(session, env, tokens, speaker, UkrainianText(dictionary))
        }

        fun dictionary(context: Context): StressDictionary? = try { StressDictionary(mapAsset(context, "speech/stress.bin")) } catch (e: Exception) { null }
    }
}

/**
 * Pysanka's English voice: Cori (Piper, trained from scratch on public-domain recordings), a VITS model run on the
 * phone. English text becomes IPA phonemes with eSpeak NG, as the voice was trained; punctuation is kept for pauses.
 */
class EnglishVoice private constructor(
    private val session: OrtSession, private val env: OrtEnvironment, private val ids: Map<String, LongArray>,
    private val espeakVoice: String, private val noise: Float, private val length: Float, private val noiseW: Float, val rate: Int,
) : Voice {
    /** The phonemes of a sentence, clause by clause, with its commas and stops (the voice pauses and rises on them). */
    fun phonemes(text: String): String {
        val out = StringBuilder()
        for ((words, mark) in clauses(text)) {
            val ph = Espeak.phonemes(words, espeakVoice)?.trim().orEmpty()
            if (ph.isEmpty()) continue
            if (out.isNotEmpty()) out.append(' ')
            out.append(ph)
            out.append(when (mark) { ',', ';', ':', '—', '–' -> ","; '?' -> "?"; '!' -> "!"; '.', '…' -> "."; else -> "" })
        }
        return out.toString()
    }

    override suspend fun read(text: String, speed: Float): Sound? {
        val ph = java.text.Normalizer.normalize(phonemes(text), java.text.Normalizer.Form.NFD)
        if (ph.isBlank()) return null
        val pad = ids["_"] ?: longArrayOf(0)
        val seq = ArrayList<Long>(ph.length * 2 + 4)
        ids["^"]?.forEach { seq += it }; pad.forEach { seq += it }
        var i = 0
        while (i < ph.length) {
            val cp = ph.codePointAt(i); val c = String(Character.toChars(cp)); i += Character.charCount(cp)
            val id = ids[c] ?: continue
            id.forEach { seq += it }; pad.forEach { seq += it }
        }
        ids["$"]?.forEach { seq += it }
        val input = seq.toLongArray()
        return kotlinx.coroutines.coroutineScope {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val scales = floatArrayOf(noise, length / speed.coerceIn(0.5f, 2.5f), noiseW)
            val options = OrtSession.RunOptions()
            val watcher = launch(kotlinx.coroutines.Dispatchers.Default) { try { kotlinx.coroutines.awaitCancellation() } finally { try { options.setTerminate(true) } catch (_: Exception) {} } }
            try {
                OnnxTensor.createTensor(env, LongBuffer.wrap(input), longArrayOf(1, input.size.toLong())).use { t ->
                    OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(input.size.toLong())), longArrayOf(1)).use { l ->
                        OnnxTensor.createTensor(env, FloatBuffer.wrap(scales), longArrayOf(3)).use { sc ->
                            session.run(mapOf("input" to t, "input_lengths" to l, "scales" to sc), options).use { r ->
                                // [1, 1, samples] or [1, samples]: the samples, whichever
                                val buf = (r[0] as OnnxTensor).floatBuffer
                                Sound(FloatArray(buf.remaining()).also { buf.get(it) }, rate)
                            }
                        }
                    }
                }
            } finally {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { watcher.cancelAndJoin() }
                options.close()
            }
        }
    }

    override fun close() { try { session.close() } catch (_: Exception) {} }

    companion object {
        private const val CONFIG = "speech/en-cori.onnx.json"
        private const val MARKS = ",;:.!?…"
        private const val CLOSING = ")]\"'»"
        /** Words whose full stop does not end a clause: «Mr. Smith», «e.g. this». */
        private val SHORT = setOf("mr", "mrs", "ms", "dr", "prof", "st", "vs", "etc", "e.g", "i.e", "no", "fig", "jr", "sr", "inc", "ltd", "co", "mt", "approx")

        /**
         * A sentence cut into clauses, each with the mark that ends it (or none). A clause ends at a mark followed by a
         * space or the end, closing quotes and brackets allowed in between («3.5», «1,000», «Mr. Smith» stay whole), and
         * at a dash: «—» anywhere, «–» between spaces («1–2» is a range). Control characters become spaces: eSpeak takes
         * some of them for commands, and a tab or a line end still parts two words.
         */
        internal fun clauses(text: String): List<Pair<String, Char?>> {
            val t = buildString(text.length) { for (c in text) append(if (c.isISOControl()) ' ' else c) }
                .replace('’', '\'').replace('‘', '\'').replace('“', '"').replace('”', '"')
            val out = ArrayList<Pair<String, Char?>>()
            var start = 0
            fun emit(end: Int, mark: Char?) {
                val w = t.substring(start, end).trim().trimEnd { it in MARKS }.trim()
                if (w.any { it.isLetterOrDigit() }) out += w to mark
            }
            var i = 0
            while (i < t.length) {
                val c = t[i]
                if (c in MARKS) {
                    var j = i + 1
                    while (j < t.length && (t[j] in MARKS || t[j] in CLOSING)) j++
                    if (j >= t.length || t[j].isWhitespace()) {
                        var k = i - 1
                        while (k >= 0 && (t[k].isLetter() || t[k] == '.')) k--
                        val word = t.substring(k + 1, i).lowercase()
                        if (!(c == '.' && word in SHORT)) {
                            // the abbreviation's dot stays for eSpeak («Mr.» is read «mister»)
                            emit(i, c); start = j
                        }
                    }
                    i = j; continue
                }
                if (c == '—' || (c == '–' && i > 0 && t[i - 1].isWhitespace() && i + 1 < t.length && t[i + 1].isWhitespace())) {
                    emit(i, c); start = i + 1
                }
                i++
            }
            emit(t.length, null)
            return out
        }

        fun open(context: Context): EnglishVoice {
            if (!Espeak.start(context)) throw IllegalStateException("espeak")
            val cfg = org.json.JSONObject(context.assets.open(CONFIG).bufferedReader().readText())
            val map = cfg.getJSONObject("phoneme_id_map")
            val ids = HashMap<String, LongArray>()
            for (k in map.keys()) { val a = map.getJSONArray(k); ids[k] = LongArray(a.length()) { a.getLong(it) } }
            val inference = cfg.optJSONObject("inference")
            val env = OrtEnvironment.getEnvironment()
            val session = OrtSession.SessionOptions().use { options ->
                options.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
                options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                options.setCPUArenaAllocator(false)
                env.createSession(mapModel(context, ua.pysanka.models.PinnedModel.CORI), options)
            }
            return EnglishVoice(
                session, env, ids, cfg.optJSONObject("espeak")?.optString("voice")?.ifEmpty { null } ?: "en-gb-x-rp",
                inference?.optDouble("noise_scale", 0.667)?.toFloat() ?: 0.667f,
                // A calm narrator's pace: the voice reads a little fast on its own
                (inference?.optDouble("length_scale", 1.0)?.toFloat() ?: 1f) * ENGLISH_PACE,
                inference?.optDouble("noise_w", 0.8)?.toFloat() ?: 0.8f,
                cfg.optJSONObject("audio")?.optInt("sample_rate", 22050) ?: 22050,
            )
        }

        /** About four syllables a second at 1,0× (Cori reads near 4,1 on her own; a touch slower is easier to follow). */
        private const val ENGLISH_PACE = 1.08f
    }
}

/**
 * eSpeak NG, used only to turn English text into phonemes for the English voice (it was trained on
 * them). Its language data comes with the app and is put in place once, in Pysanka's own files.
 */
internal object Espeak {
    private const val DATA = "speech/espeak-ng-data"
    private const val VERSION = "1.52.0-en-1"
    @Volatile private var started = false

    @Synchronized
    fun start(context: Context): Boolean {
        if (started) return true
        val root = File(context.filesDir, "espeak")
        val mark = File(root, "version")
        if (!mark.exists() || mark.readText() != VERSION) {
            // Copied beside, written to the disk, then put in place whole: a copy cut short is never used
            val fresh = File(context.filesDir, "espeak.new")
            fresh.deleteRecursively()
            copy(context, DATA, File(fresh, "espeak-ng-data"))
            java.io.FileOutputStream(File(fresh, "version")).use { it.write(VERSION.toByteArray()); it.fd.sync() }
            root.deleteRecursively()
            if (!fresh.renameTo(root)) return false
        }
        started = ua.pysanka.espeak.EspeakNative.init(root.absolutePath)
        return started
    }

    private fun copy(context: Context, from: String, to: File) {
        val names = context.assets.list(from).orEmpty()
        if (names.isEmpty()) {
            to.parentFile?.mkdirs()
            context.assets.open(from).use { i -> java.io.FileOutputStream(to).use { o -> i.copyTo(o); o.fd.sync() } }
            return
        }
        to.mkdirs()
        for (n in names) copy(context, "$from/$n", File(to, n))
    }

    fun phonemes(text: String, voice: String): String? = if (!started) null else ua.pysanka.espeak.EspeakNative.phonemes(text, voice)
}
