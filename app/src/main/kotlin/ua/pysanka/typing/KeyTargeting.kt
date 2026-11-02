package ua.pysanka.typing

import kotlin.math.ln

/**
 * Pysanka: a tap close to the edge between two letters goes to the one more likely next in the
 * word being typed (an invisible, slightly larger key; the keyboard looks the same). Only for
 * letters, only within [MARGIN] of a key's edge (a tap well inside a key is always that key), only
 * while the likely letters were worked out for exactly the word typed now, and never in password,
 * incognito or "no learning" fields (the keyboard does not publish them there).
 *
 * The chosen key is the one with the best score: how likely its letter is next ([Priors], from the
 * word list), less how far the tap is from its middle (a normal spread of taps around a key).
 */
object KeyTargeting {
    /** How far past its edge (a share of the key's width or height) a key may take a tap. */
    const val MARGIN = 0.25f

    /** A letter never in any word after this beginning still counts a little. */
    private const val FLOOR = 0.01

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
        var best = 0
        var bestScore = score(x, y, targets[0], priors)
        for (i in 1 until targets.size) {
            val s = score(x, y, targets[i], priors)
            if (s > bestScore) {
                best = i
                bestScore = s
            }
        }
        return best
    }

    private fun score(x: Float, y: Float, t: Target, priors: Priors): Double {
        // Taps spread around a key's middle with about half its size
        val dx = (x - (t.left + t.right) / 2) / (t.width / 2)
        val dy = (y - (t.top + t.bottom) / 2) / (t.height / 2)
        val p = (priors.next[t.letter] ?: 0.0).coerceAtLeast(FLOOR)
        return ln(p) - (dx * dx + dy * dy) / 2
    }

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
        private const val MAX = 0.35f

        @Volatile var dx = 0f
            private set
        @Volatile var dy = 0f
            private set

        /** A tap moved by what was learned ([w], [h]: a letter key's size). */
        fun adjust(x: Float, y: Float, w: Float, h: Float): Pair<Float, Float> = (x - dx * w) to (y - dy * h)

        /** A tap (already moved by [adjust]) that ended on [t]: what is still off is learned a little. */
        fun learn(x: Float, y: Float, t: Target) {
            val rx = (x - (t.left + t.right) / 2) / t.width
            val ry = (y - (t.top + t.bottom) / 2) / t.height
            dx = (dx + RATE * rx).coerceIn(-MAX, MAX)
            dy = (dy + RATE * ry).coerceIn(-MAX, MAX)
        }

        fun reset() {
            dx = 0f
            dy = 0f
        }
    }
}
