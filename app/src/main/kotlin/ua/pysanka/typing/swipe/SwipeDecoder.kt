package ua.pysanka.typing.swipe

import java.text.Normalizer
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Pysanka: which word a swipe over the keys meant. Every distance is in key widths, so the same
 * numbers work on any screen.
 *
 * A word is a candidate when its first and last keys are near the swipe's ends and its ideal path
 * (key centre to key centre) is not far shorter or longer than the swipe. Each candidate is then
 * judged by six measures together, in place of FlorisBoard's two:
 *
 * - **shape**: the swipe and the word's path, both scaled to the same size, point by point;
 * - **location**: the same, where they are on the keyboard;
 * - **letters in order**: every letter of the word must be passed, in its order, somewhere along
 *   the swipe (an alignment that allows slow and fast parts, so speed does not matter);
 * - **ends**: the first and last letters where the swipe starts and stops;
 * - **no detours**: every part of the swipe must lie near the word's path (a swipe through «в, і,
 *   р» is far from the path of «перемістити»);
 * - **length**: the swipe's length against the path's (people cut corners and overshoot a little);
 *
 * and then how common the word is, and whether the person often writes it after the previous word.
 * The weights were tuned on 155 real swipes by one person from the swipe practice (each half checked
 * on the other: 93-97 % right first on the half not tuned on), and still put the right word first in
 * 98 % of simulated swipes (`scripts/swipe/`, real.py and tune.py).
 */
class SwipeDecoder(keys: Map<Char, Point>, words: List<String>) {
    /** A key centre, in key widths. */
    data class Point(val x: Float, val y: Float)

    private val keyChars = keys.keys.toCharArray()
    private val kx = FloatArray(keyChars.size) { keys.getValue(keyChars[it]).x }
    private val ky = FloatArray(keyChars.size) { keys.getValue(keyChars[it]).y }
    private val keyIndex = HashMap<Char, Int>().apply { keyChars.forEachIndexed { i, c -> put(c, i) } }

    private val words = ArrayList<String>()
    private val seqs = ArrayList<IntArray>()
    private val idealLength: FloatArray
    /** Words by their first and last key. */
    private val byEnds = HashMap<Int, IntArrayBuilder>()

    init {
        val lengths = ArrayList<Float>()
        for (w in words) {
            val s = keySequence(w) ?: continue
            val i = this.words.size
            this.words += w
            seqs += s
            lengths += length(s)
            byEnds.getOrPut(s.first() * 1024 + s.last()) { IntArrayBuilder() }.add(i)
        }
        idealLength = lengths.toFloatArray()
    }

    /** The word's keys in order (a letter typed twice is one stay on its key); null if it cannot be swiped. */
    private fun keySequence(word: String): IntArray? {
        val out = IntArrayBuilder()
        for (ch in word) {
            if (ch == '\'' || ch == '’' || ch == 'ʼ' || ch == '-') continue
            val lc = ch.lowercaseChar()
            // A letter only in a key's popup (ґ on г, ъ on ь, ő on ö): swiped through that key; a
            // letter with a mark (ą, ñ, ü…) through its letter
            val k = keyIndex[lc] ?: POPUP_BASE[lc]?.let { keyIndex[it] }
                ?: keyIndex[Normalizer.normalize(lc.toString(), Normalizer.Form.NFD)[0]] ?: return null
            if (out.size == 0 || out.last() != k) out.add(k)
        }
        return if (out.size == 0) null else out.toArray()
    }

    private fun length(s: IntArray): Float {
        var l = 0f
        for (i in 1 until s.size) l += hypot(kx[s[i]] - kx[s[i - 1]], ky[s[i]] - ky[s[i - 1]])
        return l
    }

    /**
     * The [max] likeliest words for the swipe ([xs], [ys]: its first [n] points, in key widths).
     * [frequency]: 1..255 (0: never offer it); [bonus]: extra weight for a word (the person's habits).
     */
    fun decode(
        xs: FloatArray, ys: FloatArray, n: Int, max: Int,
        frequency: (String) -> Int, bonus: (String) -> Double = { 0.0 },
    ): List<String> {
        if (n < 1 || words.isEmpty()) return emptyList()
        val path = Path(xs.copyOf(n), ys.copyOf(n))
        val userLength = path.length()
        val user = path.resample(SAMPLES)
        val userNorm = user.normalized()
        val dense = path.resampleStep(STEP, MAX_DENSE)

        val starts = nearKeys(xs[0], ys[0])
        val ends = nearKeys(xs[n - 1], ys[n - 1])
        // First a cheap look (where the swipe starts and ends, its length, and how near it runs to
        // the word's keys at a few points) keeps the likeliest [SHORTLIST]; only those get the full
        // measures and their frequency looked up. Without the near-the-keys part the right word was
        // often dropped here, since thousands of words share the first and last key: on real swipes
        // the app put it first only 39 % of the time; this and the re-tuned weights together, 95 %
        val coarse = path.resample(COARSE)
        val cheap = ArrayList<Pair<Double, Int>>()
        val fx = xs[0]; val fy = ys[0]; val lx = xs[n - 1]; val ly = ys[n - 1]
        for (a in starts) for (b in ends) {
            val bucket = byEnds[a * 1024 + b] ?: continue
            val endsCost = sq(kx[a] - fx, ky[a] - fy) + sq(kx[b] - lx, ky[b] - ly)
            for (j in 0 until bucket.size) {
                val wi = bucket[j]
                val il = idealLength[wi]
                if (il > 0f && (userLength < 0.4f * il - 1.2f || userLength > 2.0f * il + 1.5f)) continue
                val lr = if (il > 0.3f) ln(max(userLength, 1e-3f) / il).toDouble() else 0.0
                val off = coarseOff(seqs[wi], il, coarse)
                cheap += (W_ENDS * endsCost + W_LENGTH * (lr - LENGTH_MEAN) * (lr - LENGTH_MEAN) + W_COARSE * off * off) to wi
            }
        }
        val shortlist = if (cheap.size > SHORTLIST) cheap.sortedBy { it.first }.take(SHORTLIST) else cheap
        val best = ArrayList<Pair<Double, String>>()
        for ((_, wi) in shortlist) {
            val word = words[wi]
            val f = frequency(word)
            if (f <= 0) continue
            val s = score(seqs[wi], idealLength[wi], user, userNorm, dense, userLength) + KAPPA * f / 255.0 + bonus(word)
            best += s to word
        }
        return best.sortedByDescending { it.first }.map { it.second }.distinct().take(max)
    }

    private fun score(seq: IntArray, il: Float, user: Path, userNorm: Path, dense: Path, userLength: Float): Double {
        val l = seq.size
        val ideal = Path(FloatArray(l) { kx[seq[it]] }, FloatArray(l) { ky[seq[it]] })
        val wg = ideal.resample(SAMPLES)
        val wn = wg.normalized()
        var shape = 0.0
        var loc = 0.0
        for (i in 0 until SAMPLES) {
            shape += hypot(wn.x[i] - userNorm.x[i], wn.y[i] - userNorm.y[i])
            loc += hypot(wg.x[i] - user.x[i], wg.y[i] - user.y[i])
        }
        shape /= SAMPLES
        loc /= SAMPLES

        // Letters in order: the cheapest monotone alignment of the letters to the swipe's points
        val m = dense.size
        fun c(i: Int, j: Int): Double {
            val dx = kx[seq[i]] - dense.x[j]
            val dy = ky[seq[i]] - dense.y[j]
            return min((dx * dx + dy * dy).toDouble(), CAP)
        }
        val visits: Double
        if (l == 1) {
            visits = c(0, 0) + c(0, m - 1)
        } else {
            var d = DoubleArray(m) { Double.POSITIVE_INFINITY }
            d[0] = c(0, 0)
            for (i in 1 until l) {
                val next = DoubleArray(m)
                var run = Double.POSITIVE_INFINITY
                for (j in 0 until m) {
                    run = min(run, d[j])
                    next[j] = c(i, j) + run
                }
                d = next
            }
            visits = d[m - 1]
        }
        val endsCost = c(0, 0) + c(l - 1, m - 1)

        // No detours: each point of the swipe near the word's path
        var detour = 0.0
        for (j in 0 until m) {
            var best = Double.POSITIVE_INFINITY
            if (l == 1) {
                val dx = dense.x[j] - kx[seq[0]]
                val dy = dense.y[j] - ky[seq[0]]
                best = (dx * dx + dy * dy).toDouble()
            } else {
                for (i in 0 until l - 1) best = min(best, segmentDistance2(dense.x[j], dense.y[j], seq[i], seq[i + 1]))
            }
            detour += min(best, CAP)
        }
        detour /= m

        val lr = if (il > 0.3f) ln(max(userLength, 1e-3f) / il).toDouble() else 0.0
        return -(W_SHAPE * shape * shape + W_LOC * loc * loc + W_VISITS * visits + W_ENDS * endsCost +
            W_DETOUR * detour + W_LENGTH * (lr - LENGTH_MEAN) * (lr - LENGTH_MEAN))
    }

    private fun sq(dx: Float, dy: Float) = min((dx * dx + dy * dy).toDouble(), CAP)

    private fun segmentDistance2(px: Float, py: Float, a: Int, b: Int): Double {
        val ax = kx[a]; val ay = ky[a]
        val abx = kx[b] - ax; val aby = ky[b] - ay
        val ab2 = abx * abx + aby * aby
        val t = if (ab2 <= 0f) 0f else (((px - ax) * abx + (py - ay) * aby) / ab2).coerceIn(0f, 1f)
        val dx = px - (ax + t * abx)
        val dy = py - (ay + t * aby)
        return (dx * dx + dy * dy).toDouble()
    }

    /**
     * The mean distance between the swipe at [COARSE] even points ([coarse]) and the word's key path
     * ([seq], [total] long) at the same points: as Path.resample, without making new arrays (it runs
     * for every word that shares the swipe's first and last key, at every update while swiping).
     */
    private fun coarseOff(seq: IntArray, total: Float, coarse: Path): Double {
        var sum = 0.0
        if (seq.size == 1 || total <= 0f) {
            val x = kx[seq[0]]; val y = ky[seq[0]]
            for (k in 0 until COARSE) { val dx = x - coarse.x[k]; val dy = y - coarse.y[k]; sum += sqrt(dx * dx + dy * dy) }
            return sum / COARSE
        }
        var seg = 0
        var segStart = 0f
        var segLen = segLength(seq, 0)
        for (k in 0 until COARSE) {
            val t = total * k / (COARSE - 1)
            while (seg < seq.size - 2 && segStart + segLen < t) {
                segStart += segLen
                seg++
                segLen = segLength(seq, seg)
            }
            val u = if (segLen > 0f) ((t - segStart) / segLen).coerceIn(0f, 1f) else 0f
            val a = seq[seg]; val b = seq[seg + 1]
            val dx = kx[a] + (kx[b] - kx[a]) * u - coarse.x[k]
            val dy = ky[a] + (ky[b] - ky[a]) * u - coarse.y[k]
            sum += sqrt(dx * dx + dy * dy)
        }
        return sum / COARSE
    }

    private fun segLength(seq: IntArray, i: Int): Float {
        val dx = kx[seq[i + 1]] - kx[seq[i]]; val dy = ky[seq[i + 1]] - ky[seq[i]]
        return sqrt(dx * dx + dy * dy)
    }

    /** The keys a swipe may have started (or ended) on: the nearest, and others not much farther. */
    private fun nearKeys(x: Float, y: Float): List<Int> {
        val d = FloatArray(keyChars.size) { hypot(kx[it] - x, ky[it] - y) }
        val order = d.indices.sortedBy { d[it] }
        return order.take(4).filter { d[it] <= d[order[0]] + 1.0f }
    }

    private class Path(val x: FloatArray, val y: FloatArray) {
        val size get() = x.size

        fun length(): Float {
            var l = 0f
            for (i in 1 until size) l += hypot(x[i] - x[i - 1], y[i] - y[i - 1])
            return l
        }

        /** [count] points evenly along the path. */
        fun resample(count: Int): Path {
            val ox = FloatArray(count)
            val oy = FloatArray(count)
            val total = length()
            if (size == 1 || total <= 0f) {
                ox.fill(x[0]); oy.fill(y[0])
                return Path(ox, oy)
            }
            var seg = 0
            var segStart = 0f
            var segLen = hypot(x[1] - x[0], y[1] - y[0])
            for (k in 0 until count) {
                val t = total * k / (count - 1)
                while (seg < size - 2 && segStart + segLen < t) {
                    segStart += segLen
                    seg++
                    segLen = hypot(x[seg + 1] - x[seg], y[seg + 1] - y[seg])
                }
                val u = if (segLen > 0f) ((t - segStart) / segLen).coerceIn(0f, 1f) else 0f
                ox[k] = x[seg] + (x[seg + 1] - x[seg]) * u
                oy[k] = y[seg] + (y[seg + 1] - y[seg]) * u
            }
            return Path(ox, oy)
        }

        fun resampleStep(step: Float, cap: Int) = resample(min(cap, max(2, (length() / step).toInt() + 1)))

        /** Scaled to its own size and centred: only the shape is left. */
        fun normalized(): Path {
            var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            for (i in 0 until size) {
                minX = min(minX, x[i]); maxX = max(maxX, x[i]); minY = min(minY, y[i]); maxY = max(maxY, y[i])
            }
            val side = max(max(maxX - minX, maxY - minY), 1e-5f)
            val cx = (minX + maxX) / 2
            val cy = (minY + maxY) / 2
            return Path(FloatArray(size) { (x[it] - cx) / side }, FloatArray(size) { (y[it] - cy) / side })
        }
    }

    private class IntArrayBuilder {
        private var data = IntArray(8)
        var size = 0
            private set
        fun add(v: Int) {
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = v
        }
        fun last() = data[size - 1]
        operator fun get(i: Int) = data[i]
        fun toArray() = data.copyOf(size)
    }

    companion object {
        private const val SAMPLES = 64
        private const val STEP = 0.25f
        private const val MAX_DENSE = 160
        private const val CAP = 4.0

        /** How many words the cheap first look passes on to the full measures. */
        private const val SHORTLIST = 400
        /** Points of the cheap look's near-the-keys measure, and its weight. */
        private const val COARSE = 12
        private const val W_COARSE = 20.0

        // ґ on г (Ukrainian, Belarusian); ё on е and ъ on ь (Russian); letters that are not a mark
        // on another letter (ł, ß, æ, œ, ø); Hungarian ő, ű on its ö, ü keys
        private val POPUP_BASE = mapOf(
            'ґ' to 'г', 'ё' to 'е', 'ъ' to 'ь', 'ł' to 'l', 'ß' to 's', 'æ' to 'a', 'œ' to 'o', 'ø' to 'o',
            'ő' to 'ö', 'ű' to 'ü',
        )

        // Tuned on 155 real swipes by one person from the swipe practice (scripts/swipe/real.py and
        // tune.py; 93-97 % right first on the half not tuned on), and still 98 % on simulated swipes
        private const val W_SHAPE = 67.947
        private const val W_LOC = 1.344
        private const val W_VISITS = 3.248
        private const val W_ENDS = 0.598
        private const val W_DETOUR = 5.612
        private const val W_LENGTH = 14.458
        private const val LENGTH_MEAN = -0.045
        private const val KAPPA = 9.853

        /** How much a word the person often writes after the previous one counts (see [decode]'s bonus). */
        // (scaled with the frequency's weight, which the real swipes lowered from 26.94; the practice has
        // no word pairs, so this was not measured on real swipes)
        fun pairBonus(pairs: Int): Double = if (pairs <= 0) 0.0 else 0.73 + 0.55 * ln(pairs.toDouble())
    }
}
