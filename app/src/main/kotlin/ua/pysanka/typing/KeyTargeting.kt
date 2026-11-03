package ua.pysanka.typing

import kotlin.math.ln

/**
 * Pysanka: a tap close to the edge between two letters goes to the one more likely next in the
 * word being typed (an invisible, slightly larger key; the keyboard looks the same). Only for
 * letters, only within [MARGIN] of a key's edge (a tap well inside a key is always that key), only
 * while the likely letters were worked out for exactly the word typed now, and never in password,
 * incognito or "no learning" fields (the keyboard does not publish them there), nor while a word
 * the person taught the keyboard is being typed.
 *
 * A neighbour takes the tap only if its letter is more likely than the one drawn there, and by
 * more the deeper the tap is inside the drawn key: at the edge any better odds will do, at
 * [MARGIN] inside they must be about [AT_MARGIN] times better.
 */
object KeyTargeting {
    /** How far into a key (a share of its width or height) a neighbour may take a tap. */
    const val MARGIN = 0.15f

    /** A letter never in any word after this beginning still counts a little. */
    private const val FLOOR = 0.05

    /** How much better the odds must be at [MARGIN] inside the drawn key. */
    private const val AT_MARGIN = 7.0

    /** The likely next letters after [prefix] (in small letters), summing to 1. */
    class Priors(val prefix: String, val next: Map<Char, Double>)

    @Volatile private var priors: Priors? = null

    /** The likely next letters, worked out with the suggestions; null: none (then taps are as drawn). */
    fun publish(p: Priors?) {
        priors = p
    }

    /** The priors, if they were worked out for exactly [composing] (taps can be faster than suggestions). */
    fun current(composing: String): Priors? = priors?.takeIf { it.prefix == PersonalWords.key(composing) }

    /** A key that may take the tap: its bounds and its letter (small). */
    class Target(val left: Float, val top: Float, val right: Float, val bottom: Float, val letter: Char) {
        val width get() = right - left
        val height get() = bottom - top

        fun near(x: Float, y: Float): Boolean {
            val mx = width * MARGIN
            val my = height * MARGIN
            return x >= left - mx && x <= right + mx && y >= top - my && y <= bottom + my
        }
    }

    /**
     * Which of [targets] the tap at ([x], [y]) is for: index 0 is the key the tap is on; the others
     * are letters whose edge is within [MARGIN] of the tap.
     */
    fun pick(x: Float, y: Float, targets: List<Target>, priors: Priors): Int {
        if (priors.next.isEmpty()) return 0
        val drawn = prior(targets[0], priors)
        var best = 0
        var bestGain = 0.0
        for (i in 1 until targets.size) {
            val t = targets[i]
            val p = prior(t, priors)
            if (p <= drawn) continue
            // How far outside this key the tap is, as a share of the margin (0..1 for a candidate)
            val ox = maxOf(t.left - x, x - t.right, 0f) / (t.width * MARGIN)
            val oy = maxOf(t.top - y, y - t.bottom, 0f) / (t.height * MARGIN)
            val needed = (ox * ox + oy * oy) * ln(AT_MARGIN)
            val gain = ln(p / drawn) - needed
            if (gain > bestGain) {
                best = i
                bestGain = gain
            }
        }
        return best
    }

    private fun prior(t: Target, priors: Priors) = (priors.next[t.letter] ?: 0.0).coerceAtLeast(FLOOR)

    /**
     * Pysanka: where this person's taps land around a key's middle, on average (in shares of a key's
     * size), learned while typing letters and taken off each tap, so a hand that always hits a bit
     * low or to the left lands where it means to. Kept in memory only (it is learned again within
     * a few dozen taps), never in password or private fields.
     */
    object Calibration {
        /** How fast it follows (each tap moves it by this share of what is left). */
        private const val RATE = 0.03f

        /** The most it corrects, as a share of a key. */
        private const val MAX = 0.2f

        /**
         * Besides the offset for the whole keyboard, one for each part of it ([ZONES]: rows and the
         * left, middle and right thirds), learned a little faster but capped lower: thumbs miss
         * differently at the far edges and in the bottom row than in the middle.
         */
        const val ZONES = 12
        private const val ZONE_RATE = 0.05f
        private const val ZONE_MAX = 0.12f

        @Volatile var dx = 0f
            private set
        @Volatile var dy = 0f
            private set
        private val zx = FloatArray(ZONES)
        private val zy = FloatArray(ZONES)

        /**
         * The part of the keyboard a tap at ([x], [y]) is in: 4 bands from the top, 3 across of a
         * keyboard [width] × [height]; -1 if unknown.
         */
        fun zone(x: Float, y: Float, width: Float, height: Float): Int {
            if (width <= 0f || height <= 0f) return -1
            val col = (x / width * 3).toInt().coerceIn(0, 2)
            val row = (y / height * 4).toInt().coerceIn(0, 3)
            return row * 3 + col
        }

        /** A tap moved by what was learned ([w], [h]: a letter key's size; [zone]: see [zone]). */
        fun adjust(x: Float, y: Float, w: Float, h: Float, zone: Int = -1): Pair<Float, Float> {
            val ox = dx + (if (zone in 0 until ZONES) zx[zone] else 0f)
            val oy = dy + (if (zone in 0 until ZONES) zy[zone] else 0f)
            return (x - ox * w) to (y - oy * h)
        }

        /** A tap (already moved by [adjust]) that ended on [t]: what is still off is learned a little. */
        fun learn(x: Float, y: Float, t: Target, zone: Int = -1) {
            val rx = (x - (t.left + t.right) / 2) / t.width
            val ry = (y - (t.top + t.bottom) / 2) / t.height
            dx = (dx + RATE * rx).coerceIn(-MAX, MAX)
            dy = (dy + RATE * ry).coerceIn(-MAX, MAX)
            if (zone in 0 until ZONES) {
                zx[zone] = (zx[zone] + ZONE_RATE * rx).coerceIn(-ZONE_MAX, ZONE_MAX)
                zy[zone] = (zy[zone] + ZONE_RATE * ry).coerceIn(-ZONE_MAX, ZONE_MAX)
            }
        }

        fun reset() {
            dx = 0f
            dy = 0f
            zx.fill(0f)
            zy.fill(0f)
        }
    }
}
