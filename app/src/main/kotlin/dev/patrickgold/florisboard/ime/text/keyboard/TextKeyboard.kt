/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.text.keyboard

import dev.patrickgold.florisboard.ime.keyboard.Key
import dev.patrickgold.florisboard.ime.keyboard.Keyboard
import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.ime.popup.PopupMapping
import kotlin.math.abs

class TextKeyboard(
    val arrangement: Array<Array<TextKey>>,
    override val mode: KeyboardMode,
    val extendedPopupMapping: PopupMapping?,
    val extendedPopupMappingDefault: PopupMapping?,
) : Keyboard() {
    val rowCount: Int
        get() = arrangement.size

    val keyCount: Int
        get() = arrangement.sumOf { it.size }

    override fun getKeyForPos(pointerX: Float, pointerY: Float): TextKey? {
        for (key in keys()) {
            if (key.touchBounds.contains(pointerX, pointerY)) {
                return key
            }
        }
        return null
    }

    private var pysankaWidth = 0f
    private var pysankaHeight = 0f

    /**
     * Pysanka: the key for a tap: as drawn, except that near the edge between two letters the one
     * more likely next in [composing] takes it (see [ua.pysanka.typing.KeyTargeting]).
     */
    fun pysankaKeyForTap(rawX: Float, rawY: Float, composing: String, calibrate: Boolean): TextKey? {
        val calibration = ua.pysanka.typing.KeyTargeting.Calibration
        val drawn = getKeyForPos(rawX, rawY) ?: return null
        val drawnLetter = pysankaLetter(drawn) ?: return drawn
        // The person's usual offset taken off, but only ever from one letter to another
        val zone = calibration.zone(rawX, rawY, pysankaWidth, pysankaHeight)
        val (pointerX, pointerY) = if (calibrate) {
            drawn.touchBounds.let { calibration.adjust(rawX, rawY, it.right - it.left, it.bottom - it.top, zone) }
        } else {
            rawX to rawY
        }
        val hit = getKeyForPos(pointerX, pointerY)?.takeIf { pysankaLetter(it) != null } ?: drawn
        val adjusted = !(hit === drawn && getKeyForPos(pointerX, pointerY) !== drawn)
        val (x, y) = if (adjusted) pointerX to pointerY else rawX to rawY
        val letter = pysankaLetter(hit) ?: drawnLetter
        val priors = ua.pysanka.typing.KeyTargeting.current(composing)
        val near = if (priors == null || priors.next.isEmpty()) {
            emptyList()
        } else {
            keys().asSequence().filter { it !== hit && it.isEnabled }
                .mapNotNull { k -> pysankaLetter(k)?.let { k to pysankaTarget(k, it) } }
                .filter { (_, t) -> t.near(x, y) }.toList()
        }
        val i = if (near.isEmpty()) 0 else {
            ua.pysanka.typing.KeyTargeting.pick(x, y, listOf(pysankaTarget(hit, letter)) + near.map { it.second }, priors!!)
        }
        // Learned from taps that went to the key they were on (a tap given to a neighbour says little),
        // and only where the offset was taken off (a raw tap still has it in)
        if (calibrate && adjusted && i == 0) calibration.learn(x, y, pysankaTarget(hit, letter), zone)
        return if (i == 0) hit else near[i - 1].first
    }

    private fun pysankaLetter(key: TextKey): Char? {
        val data = key.computedData
        if (data.type != dev.patrickgold.florisboard.ime.text.key.KeyType.CHARACTER || data.code <= 0) return null
        if (!Character.isLetter(data.code) || Character.charCount(data.code) != 1) return null
        // Alphabets with word lists only (not CJK and others, where a key is not one letter of a word)
        val script = Character.UnicodeScript.of(data.code)
        if (script != Character.UnicodeScript.LATIN && script != Character.UnicodeScript.CYRILLIC && script != Character.UnicodeScript.GREEK) return null
        return ua.pysanka.typing.PersonalWords.key(data.code.toChar().toString()).singleOrNull()
    }

    private fun pysankaTarget(key: TextKey, letter: Char) = key.touchBounds.let {
        ua.pysanka.typing.KeyTargeting.Target(it.left, it.top, it.right, it.bottom, letter)
    }

    override fun layout(
        keyboardWidth: Float,
        keyboardHeight: Float,
        desiredKey: Key,
        extendTouchBoundariesDownwards: Boolean,
    ) {
        if (arrangement.isEmpty()) return
        // Pysanka: another size (turned, one-handed): the learned tap offset starts again
        if (keyboardWidth != pysankaWidth || keyboardHeight != pysankaHeight) {
            if (pysankaWidth != 0f) ua.pysanka.typing.KeyTargeting.Calibration.reset()
            pysankaWidth = keyboardWidth
            pysankaHeight = keyboardHeight
        }

        val desiredTouchBounds = desiredKey.touchBounds
        val desiredVisibleBounds = desiredKey.visibleBounds
        if (desiredTouchBounds.isEmpty() || desiredVisibleBounds.isEmpty()) return
        if (keyboardWidth.isNaN() || keyboardHeight.isNaN()) return
        val rowMarginH = abs(desiredTouchBounds.width - desiredVisibleBounds.width)
        val rowMarginV = (keyboardHeight - desiredTouchBounds.height * rowCount.toFloat()) / (rowCount - 1).coerceAtLeast(1).toFloat()

        for ((r, row) in rows().withIndex()) {
            val posY = (desiredTouchBounds.height + rowMarginV) * r
            val availableWidth = (keyboardWidth - rowMarginH) / desiredTouchBounds.width
            var requestedWidth = 0.0f
            var shrinkSum = 0.0f
            var growSum = 0.0f
            for (key in row) {
                requestedWidth += key.flayWidthFactor
                shrinkSum += key.flayShrink
                growSum += key.flayGrow
            }
            if (requestedWidth <= availableWidth) {
                // Requested with is smaller or equal to the available with, so we can grow
                val additionalWidth = availableWidth - requestedWidth
                var posX = rowMarginH / 2.0f
                for ((k, key) in row.withIndex()) {
                    val keyWidth = desiredTouchBounds.width * when (growSum) {
                        0.0f -> when (k) {
                            0, row.size - 1 -> key.flayWidthFactor + additionalWidth / 2.0f
                            else -> key.flayWidthFactor
                        }
                        else -> key.flayWidthFactor + additionalWidth * (key.flayGrow / growSum)
                    }
                    key.touchBounds.apply {
                        left = posX
                        top = posY
                        right = posX + keyWidth
                        bottom = posY + desiredTouchBounds.height
                    }
                    key.visibleBounds.apply {
                        left = key.touchBounds.left + abs(desiredTouchBounds.left - desiredVisibleBounds.left) + when {
                            growSum == 0.0f && k == 0 -> ((additionalWidth / 2.0f) * desiredTouchBounds.width)
                            else -> 0.0f
                        }
                        top = key.touchBounds.top + abs(desiredTouchBounds.top - desiredVisibleBounds.top)
                        right = key.touchBounds.right - abs(desiredTouchBounds.right - desiredVisibleBounds.right) - when {
                            growSum == 0.0f && k == row.size - 1 -> ((additionalWidth / 2.0f) * desiredTouchBounds.width)
                            else -> 0.0f
                        }
                        bottom = key.touchBounds.bottom - abs(desiredTouchBounds.bottom - desiredVisibleBounds.bottom)
                    }
                    posX += keyWidth
                    // After-adjust touch bounds for the row margin
                    key.touchBounds.apply {
                        if (k == 0) {
                            left = 0.0f
                        } else if (k == row.size - 1) {
                            right = keyboardWidth
                        }
                        if (extendTouchBoundariesDownwards && r + 1 == arrangement.size) {
                            bottom += height
                        }
                    }
                }
            } else {
                // Requested size too big, must shrink.
                val clippingWidth = requestedWidth - availableWidth
                var posX = rowMarginH / 2.0f
                for ((k, key) in row.withIndex()) {
                    val keyWidth = desiredTouchBounds.width * if (key.flayShrink == 0.0f) {
                        key.flayWidthFactor
                    } else {
                        key.flayWidthFactor - clippingWidth * (key.flayShrink / shrinkSum)
                    }
                    key.touchBounds.apply {
                        left = posX
                        top = posY
                        right = posX + keyWidth
                        bottom = posY + desiredTouchBounds.height
                    }
                    key.visibleBounds.apply {
                        left = key.touchBounds.left + abs(desiredTouchBounds.left - desiredVisibleBounds.left)
                        top = key.touchBounds.top + abs(desiredTouchBounds.top - desiredVisibleBounds.top)
                        right = key.touchBounds.right - abs(desiredTouchBounds.right - desiredVisibleBounds.right)
                        bottom = key.touchBounds.bottom - abs(desiredTouchBounds.bottom - desiredVisibleBounds.bottom)
                    }
                    posX += keyWidth
                    // After-adjust touch bounds for the row margin
                    key.touchBounds.apply {
                        if (k == 0) {
                            left = 0.0f
                        } else if (k == row.size - 1) {
                            right = keyboardWidth
                        }
                        if (extendTouchBoundariesDownwards && r + 1 == arrangement.size) {
                            bottom += height
                        }
                    }
                }
            }
        }
    }

    override fun keys(): Iterator<TextKey> {
        return TextKeyboardIterator(arrangement)
    }

    fun rows(): Iterator<Array<TextKey>> {
        return arrangement.iterator()
    }

    class TextKeyboardIterator internal constructor(
        private val arrangement: Array<Array<TextKey>>
    ) : Iterator<TextKey> {
        private var rowIndex: Int = 0
        private var keyIndex: Int = 0

        override fun hasNext(): Boolean {
            return rowIndex < arrangement.size && keyIndex < arrangement[rowIndex].size
        }

        override fun next(): TextKey {
            val next = arrangement[rowIndex][keyIndex]
            if (keyIndex + 1 == arrangement[rowIndex].size) {
                rowIndex++
                keyIndex = 0
            } else {
                keyIndex++
            }
            return next
        }
    }
}
