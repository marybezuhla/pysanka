package ua.pysanka.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitKeyboardTest {
    @Test fun aGapBetweenTheHalves() {
        // Ten keys of 100 across 1000, and a space bar from 300 to 700
        val keys = (0 until 10).map { SplitKeyboard.Bounds(it * 100f, it * 100f + 100f, it * 100f + 5f, it * 100f + 95f) }
        val space = SplitKeyboard.Bounds(300f, 700f, 305f, 695f, isSpace = true)
        SplitKeyboard.apply(1000f, 200f, keys + space)
        // Narrowed to 800: keys of 80; the left half ends at 400, the right one starts at 600
        assertEquals(320f, keys[3].touchRight, 0.01f)
        assertEquals(600f, keys[5].touchLeft, 0.01f)
        assertEquals(1000f, keys[9].touchRight, 0.01f)
        // The space bar spans the gap
        assertEquals(240f, space.touchLeft, 0.01f)
        assertEquals(760f, space.touchRight, 0.01f)
    }

    @Test fun aLetterAcrossTheMiddleGoesToOneSide() {
        // Nine keys of 100 starting at 50 (a row with an odd count): the fifth is across the middle
        val keys = (0 until 9).map { SplitKeyboard.Bounds(50f + it * 100f, 150f + it * 100f, 55f + it * 100f, 145f + it * 100f) }
        SplitKeyboard.apply(1000f, 200f, keys)
        val middle = keys[4]
        // Narrowed: 370..450 centre 410 > 400: it goes right, starting at 600, not stretched over the gap
        assertEquals(600f, middle.touchLeft, 0.01f)
        assertTrue(middle.touchRight - middle.touchLeft <= 80.01f)
        assertTrue(keys[3].touchRight <= 400.01f)
    }

    @Test fun whenToSplit() {
        // A phone held sideways (900 dp wide, its shorter side 400): whole, unless chosen
        assertEquals(0f, SplitKeyboard.gap(SplitKeyboard.AUTO, 2000f, 900f, smallestDp = 400), 0f)
        assertTrue(SplitKeyboard.gap(SplitKeyboard.ON, 2000f, 900f, smallestDp = 400) > 0f)
        // A tablet or an unfolded phone, wide enough
        assertTrue(SplitKeyboard.gap(SplitKeyboard.AUTO, 2000f, 900f, smallestDp = 700) > 0f)
        // A tablet held upright, narrow keyboard: whole
        assertEquals(0f, SplitKeyboard.gap(SplitKeyboard.AUTO, 1200f, 600f, smallestDp = 600), 0f)
        assertEquals(0f, SplitKeyboard.gap(SplitKeyboard.OFF, 2000f, 900f, smallestDp = 700), 0f)
    }
}
