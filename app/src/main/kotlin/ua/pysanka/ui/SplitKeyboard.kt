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
     * moves right by [gap]; a key across the middle (the space bar) is stretched over it.
     * [bounds]: each key's left and right, of its touch area and of what is drawn.
     */
    fun apply(width: Float, gap: Float, keys: List<Bounds>) {
        if (gap <= 0f || width <= gap) return
        val s = (width - gap) / width
        val mid = (width - gap) / 2
        for (k in keys) {
            k.touchLeft *= s; k.touchRight *= s; k.drawnLeft *= s; k.drawnRight *= s
            when {
                k.touchLeft >= mid - 1f -> {
                    k.touchLeft += gap; k.touchRight += gap; k.drawnLeft += gap; k.drawnRight += gap
                }
                k.touchRight > mid + 1f -> {
                    k.touchRight += gap; k.drawnRight += gap
                }
            }
        }
    }

    class Bounds(var touchLeft: Float, var touchRight: Float, var drawnLeft: Float, var drawnRight: Float)
}
