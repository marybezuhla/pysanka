package ua.pysanka.typing

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import ua.pysanka.typing.HardwareKeys.Action

/** A physical keyboard, pressed key by key (as Android hands the keys to the keyboard). */
class HardwareKeysTest {
    private fun press(
        key: Int, lang: String = "uk", shift: Boolean = false, caps: Boolean = false,
        ctrl: Boolean = false, alt: Boolean = false, meta: Boolean = false, system: Char = 'ж',
    ) = HardwareKeys.action(key, lang, shift, caps, ctrl, alt, meta, system.code)

    private fun type(lang: String, vararg keys: Int) = keys.joinToString("") { (press(it, lang) as Action.Type).text }

    @Test fun typesInTheKeyboardsLanguage() {
        assertEquals("привіт", type("uk", KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_N))
        assertEquals("hello", type("en", KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_L, KeyEvent.KEYCODE_L, KeyEvent.KEYCODE_O))
    }

    @Test fun ukrainianLettersAndApostrophe() {
        assertEquals(Action.Type("ї"), press(KeyEvent.KEYCODE_RIGHT_BRACKET))
        assertEquals(Action.Type("є"), press(KeyEvent.KEYCODE_APOSTROPHE))
        assertEquals(Action.Type("ґ"), press(KeyEvent.KEYCODE_BACKSLASH))
        assertEquals(Action.Type("ʼ"), press(KeyEvent.KEYCODE_GRAVE))
        assertEquals(Action.Type(","), press(KeyEvent.KEYCODE_SLASH, shift = true))
    }

    @Test fun englishKeepsALatinSystemLayout() {
        // The system types «q» (or «a» on AZERTY) for this key: its own layout is kept
        assertEquals(Action.Pass, press(KeyEvent.KEYCODE_A, lang = "en", system = 'q'))
        // The system is set to Ukrainian: English is typed as on a US keyboard
        assertEquals(Action.Type("a"), press(KeyEvent.KEYCODE_A, lang = "en", system = 'ф'))
    }

    @Test fun shiftAndCapsLock() {
        assertEquals(Action.Type("П"), press(KeyEvent.KEYCODE_G, shift = true))
        assertEquals(Action.Type("П"), press(KeyEvent.KEYCODE_G, caps = true))
        assertEquals(Action.Type("п"), press(KeyEvent.KEYCODE_G, shift = true, caps = true))
        // Caps Lock leaves digits alone
        assertEquals(Action.Type("1"), press(KeyEvent.KEYCODE_1, caps = true))
        assertEquals(Action.Type("№"), press(KeyEvent.KEYCODE_3, shift = true))
    }

    @Test fun languageSwitches() {
        assertEquals(Action.SwitchLanguage, press(KeyEvent.KEYCODE_SPACE, ctrl = true))
        assertEquals(Action.ArmSwitch, press(KeyEvent.KEYCODE_SHIFT_LEFT, alt = true))
        assertEquals(Action.ArmSwitch, press(KeyEvent.KEYCODE_ALT_LEFT, shift = true))
        // Right Alt is AltGr on many keyboards
        assertEquals(Action.Pass, press(KeyEvent.KEYCODE_ALT_RIGHT, shift = true))
        // Caps Lock does not turn ʼ into ₴
        assertEquals(Action.Type("ʼ"), press(KeyEvent.KEYCODE_GRAVE, caps = true))
        assertEquals(Action.SwitchLanguage, press(KeyEvent.KEYCODE_LANGUAGE_SWITCH))
    }

    @Test fun shortcutsAndOtherKeysPass() {
        assertEquals(Action.Pass, press(KeyEvent.KEYCODE_C, ctrl = true))
        assertEquals(Action.Pass, press(KeyEvent.KEYCODE_TAB, alt = true))
        assertEquals(Action.Pass, press(KeyEvent.KEYCODE_SPACE))
        assertEquals(Action.Pass, press(KeyEvent.KEYCODE_DEL))
        assertEquals(Action.Pass, press(KeyEvent.KEYCODE_A, lang = "de"))
    }
}
