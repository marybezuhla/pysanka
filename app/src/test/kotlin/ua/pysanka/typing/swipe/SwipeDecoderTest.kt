package ua.pysanka.typing.swipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Swipes drawn as a person would (keys missed by a third of a key, corners cut, uneven speed) over
 * the Ukrainian and English layouts.
 */
class SwipeDecoderTest {
    private fun layout(vararg rows: Pair<String, Float>): Map<Char, SwipeDecoder.Point> = buildMap {
        rows.forEachIndexed { r, (row, offset) ->
            row.forEachIndexed { i, c -> put(c, SwipeDecoder.Point(offset + i + 0.5f, r * 1.4f + 0.7f)) }
        }
    }

    private val uk = layout("йцукенгшщзхї" to 0f, "фівапролджє" to 0.5f, "ячсмитьбю" to 1.5f)
    private val en = layout("qwertyuiop" to 0f, "asdfghjkl" to 0.5f, "zxcvbnm" to 1.5f)

    /** A swipe through [word]'s keys: each target off by up to a third of a key, corners rounded. */
    private fun swipe(word: String, keys: Map<Char, SwipeDecoder.Point>, seed: Int): Pair<FloatArray, FloatArray> {
        val rnd = Random(seed)
        val targets = word.map { keys.getValue(it) }.map {
            SwipeDecoder.Point(it.x + (rnd.nextFloat() - 0.5f) * 0.6f, it.y + (rnd.nextFloat() - 0.5f) * 0.5f)
        }
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()
        for (i in 0 until targets.size - 1) {
            val a = targets[i]
            val b = targets[i + 1]
            // A point every fifth of a key or so (the finger moves at an uneven speed)
            val dist = kotlin.math.hypot(b.x - a.x, b.y - a.y)
            val steps = maxOf(2, (dist / (0.15f + rnd.nextFloat() * 0.1f)).toInt())
            for (k in 0 until steps) {
                val t = k / steps.toFloat()
                xs += a.x + (b.x - a.x) * t
                ys += a.y + (b.y - a.y) * t
            }
        }
        xs += targets.last().x
        ys += targets.last().y
        // Corners cut: a moving average over a few points
        val sx = FloatArray(xs.size) { i -> (maxOf(0, i - 2)..minOf(xs.size - 1, i + 2)).map { xs[it] }.average().toFloat() }
        val sy = FloatArray(ys.size) { i -> (maxOf(0, i - 2)..minOf(ys.size - 1, i + 2)).map { ys[it] }.average().toFloat() }
        sx[0] = xs[0]; sy[0] = ys[0]; sx[sx.size - 1] = xs.last(); sy[sy.size - 1] = ys.last()
        return sx to sy
    }

    private fun top(decoder: SwipeDecoder, word: String, keys: Map<Char, SwipeDecoder.Point>, freq: Map<String, Int>, seed: Int = 1): String? {
        val (xs, ys) = swipe(word, keys, seed)
        return decoder.decode(xs, ys, xs.size, 3, { freq[it] ?: 0 }).firstOrNull()
    }

    private val ukWords = mapOf(
        "перевірити" to 150, "перемістити" to 150, "працює" to 190, "працюю" to 160, "привіт" to 200,
        "свайп" to 100, "він" to 230, "вони" to 220, "як" to 240, "якщо" to 220, "дякую" to 200,
        "пиши" to 150, "писанка" to 120, "клавіатура" to 150, "добре" to 210,
    )

    @Test fun ukrainianWordsFromTheOwnersVideo() {
        val d = SwipeDecoder(uk, ukWords.keys.toList())
        for (w in listOf("перевірити", "перемістити", "працює", "працюю", "привіт", "клавіатура")) {
            for (seed in 1..3) assertEquals("$w (seed $seed)", w, top(d, w, uk, ukWords, seed))
        }
    }

    @Test fun englishWords() {
        val words = mapOf("hello" to 220, "help" to 210, "world" to 200, "would" to 230, "keyboard" to 150, "quiet" to 150, "quite" to 180)
        val d = SwipeDecoder(en, words.keys.toList())
        for (w in listOf("hello", "world", "keyboard", "quite")) assertEquals(w, top(d, w, en, words))
    }

    @Test fun neverAWordWithoutFrequency() {
        val d = SwipeDecoder(uk, ukWords.keys.toList())
        val (xs, ys) = swipe("привіт", uk, 1)
        val out = d.decode(xs, ys, xs.size, 5, { if (it == "привіт") 0 else ukWords[it] ?: 0 })
        assertTrue("привіт" !in out)
    }

    @Test fun lettersOutsideTheLayoutAreSkipped() {
        // An apostrophe is not on the swipe path
        val words = mapOf("мʼясо" to 150, "місто" to 150)
        val d = SwipeDecoder(uk, words.keys.toList())
        assertEquals("мʼясо", top(d, "мясо", uk, words))
    }
}
