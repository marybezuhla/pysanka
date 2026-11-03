package ua.pysanka.ui

/**
 * Pysanka: on a wide screen the keys are not stretched across it: the keyboard splits in two
 * halves, one under each thumb, with a gap between them (as on tablets and unfolded phones held
 * sideways). The keys of each half keep the size they would have on a phone.
 */
object SplitKeyboard {
    const val AUTO = "auto"
    const val ON = "on"
    const val OFF = "off"

    /** From this wide (dp) the keyboard of a screen held sideways splits by itself. */
    private const val AUTO_MIN_DP = 640f

    /** How wide the gap is ([widthPx]: the keyboard's width), or 0 for a whole keyboard. */
    fun gap(mode: String, widthPx: Float, widthDp: Float, landscape: Boolean): Float = when (mode) {
        OFF -> 0f
        ON -> widthPx * gapShare(widthDp)
        else -> if (landscape && widthDp >= AUTO_MIN_DP) widthPx * gapShare(widthDp) else 0f
    }

    /** The wider the screen, the wider the gap: each half about as wide as a phone's keyboard (~360 dp). */
    private fun gapShare(widthDp: Float): Float = ((widthDp - 2 * 340f) / widthDp).coerceIn(0.12f, 0.4f)

    /**
     * Moves laid-out keys apart: everything is narrowed into ([width] - [gap]), then the right half
     * moves right by [gap]. Only the space bar is stretched over the gap; any other key across the
     * middle (a row with an odd number of keys) goes to the side its centre is on and is cut at the
     * middle. [keys]: each key's left and right, of its touch area and of what is drawn.
     */
    fun apply(width: Float, gap: Float, keys: List<Bounds>) {
        if (gap <= 0f || width <= gap) return
        val s = (width - gap) / width
        val mid = (width - gap) / 2
        for (k in keys) {
            k.touchLeft *= s; k.touchRight *= s; k.drawnLeft *= s; k.drawnRight *= s
            val across = k.touchLeft < mid - 1f && k.touchRight > mid + 1f
            when {
                across && k.isSpace -> {
                    k.touchRight += gap; k.drawnRight += gap
                }
                across && (k.touchLeft + k.touchRight) / 2 < mid -> {
                    // Stays left, cut at the middle
                    val margin = k.touchRight - k.drawnRight
                    k.touchRight = mid; k.drawnRight = minOf(k.drawnRight, mid - margin)
                }
                across -> {
                    // Goes right, starting at the middle
                    val margin = k.drawnLeft - k.touchLeft
                    k.touchLeft = mid; k.drawnLeft = maxOf(k.drawnLeft, mid + margin)
                    k.touchLeft += gap; k.touchRight += gap; k.drawnLeft += gap; k.drawnRight += gap
                }
                k.touchLeft >= mid - 1f -> {
                    k.touchLeft += gap; k.touchRight += gap; k.drawnLeft += gap; k.drawnRight += gap
                }
            }
        }
    }

    class Bounds(var touchLeft: Float, var touchRight: Float, var drawnLeft: Float, var drawnRight: Float, val isSpace: Boolean = false)
}
