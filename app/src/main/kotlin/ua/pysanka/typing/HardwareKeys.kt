package ua.pysanka.typing

import android.view.KeyEvent

/**
 * Pysanka: a physical keyboard types in the keyboard's language, as on screen. Android otherwise
 * types with the system's physical layout (English, unless set otherwise in the system settings),
 * so switching the language in the keyboard changed nothing. The keys are taken by their place
 * (as on a US keyboard), so the system's own layout setting does not matter.
 *
 * English keeps the system's layout when that one types Latin letters (AZERTY, German, Dvorak stay
 * as set); only over a layout of another alphabet is it typed as on a US keyboard.
 *
 * The language switches with Ctrl+Space, the keyboard's own language key, or left Alt and Shift
 * pressed together and let go with nothing else in between (as on a computer).
 */
object HardwareKeys {
    sealed interface Action {
        /** Not Pysanka's: the system or the app handles it. */
        data object Pass : Action
        data object SwitchLanguage : Action
        /** Left Alt and Shift held: the language switches when one is let go, if nothing else was pressed. */
        data object ArmSwitch : Action
        data class Type(val text: String) : Action
    }

    /**
     * What a key pressed on a physical keyboard does. [language]: the keyboard's ("uk", "en"…);
     * [shift], [caps]: Shift held, Caps Lock on; [ctrl], [alt], [meta]: those held.
     */
    /**
     * [systemChar]: what the system's own layout types on the A key (0 if unknown): it tells
     * whether that layout is Latin, for every key alike, so over a Cyrillic one English digits and
     * signs are US ones too (Shift+2 is @, not ");
     * [altLeft]: the left Alt is held (the right one is AltGr on many keyboards).
     */
    fun action(
        keyCode: Int, language: String, shift: Boolean, caps: Boolean, ctrl: Boolean, alt: Boolean, meta: Boolean,
        systemChar: Int = 0, altLeft: Boolean = alt,
    ): Action {
        if (keyCode == KeyEvent.KEYCODE_LANGUAGE_SWITCH) return Action.SwitchLanguage
        if (ctrl && !alt && !meta && keyCode == KeyEvent.KEYCODE_SPACE) return Action.SwitchLanguage
        val shiftKey = keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT
        if ((shiftKey && altLeft || keyCode == KeyEvent.KEYCODE_ALT_LEFT && shift) && !ctrl && !meta) return Action.ArmSwitch
        // Shortcuts (Ctrl+C, Alt+Tab…) are the app's and the system's
        if (ctrl || alt || meta) return Action.Pass
        val map = MAPS[language] ?: return Action.Pass
        // English over a Latin layout: the system's own (AZERTY, German…) is kept
        if (language == "en" && !(Character.isLetter(systemChar) && Character.UnicodeScript.of(systemChar) != Character.UnicodeScript.LATIN)) {
            return Action.Pass
        }
        val (lower, upper) = map[keyCode] ?: return Action.Pass
        // Caps Lock turns letters only, as on a computer (ʼ is not one here)
        val up = if (lower.first().isLetter() && lower != "ʼ") shift xor caps else shift
        return Action.Type(if (up) upper else lower)
    }

    private fun keys(vararg pairs: Pair<Int, String>) = pairs.associate { (k, s) -> k to (s[0].toString() to s[1].toString()) }

    private val COMMON = arrayOf(
        KeyEvent.KEYCODE_1 to "1!", KeyEvent.KEYCODE_2 to "2@", KeyEvent.KEYCODE_3 to "3#", KeyEvent.KEYCODE_4 to "4$",
        KeyEvent.KEYCODE_5 to "5%", KeyEvent.KEYCODE_6 to "6^", KeyEvent.KEYCODE_7 to "7&", KeyEvent.KEYCODE_8 to "8*",
        KeyEvent.KEYCODE_9 to "9(", KeyEvent.KEYCODE_0 to "0)", KeyEvent.KEYCODE_MINUS to "-_", KeyEvent.KEYCODE_EQUALS to "=+",
    )

    /** The Ukrainian layout (as in Windows and macOS: ʼ on the key left of 1, ґ next to Enter). */
    private val UK = keys(
        KeyEvent.KEYCODE_GRAVE to "ʼ₴", KeyEvent.KEYCODE_1 to "1!", KeyEvent.KEYCODE_2 to "2\"", KeyEvent.KEYCODE_3 to "3№",
        KeyEvent.KEYCODE_4 to "4;", KeyEvent.KEYCODE_5 to "5%", KeyEvent.KEYCODE_6 to "6:", KeyEvent.KEYCODE_7 to "7?",
        KeyEvent.KEYCODE_8 to "8*", KeyEvent.KEYCODE_9 to "9(", KeyEvent.KEYCODE_0 to "0)", KeyEvent.KEYCODE_MINUS to "-_",
        KeyEvent.KEYCODE_EQUALS to "=+",
        KeyEvent.KEYCODE_Q to "йЙ", KeyEvent.KEYCODE_W to "цЦ", KeyEvent.KEYCODE_E to "уУ", KeyEvent.KEYCODE_R to "кК",
        KeyEvent.KEYCODE_T to "еЕ", KeyEvent.KEYCODE_Y to "нН", KeyEvent.KEYCODE_U to "гГ", KeyEvent.KEYCODE_I to "шШ",
        KeyEvent.KEYCODE_O to "щЩ", KeyEvent.KEYCODE_P to "зЗ", KeyEvent.KEYCODE_LEFT_BRACKET to "хХ", KeyEvent.KEYCODE_RIGHT_BRACKET to "їЇ",
        KeyEvent.KEYCODE_BACKSLASH to "ґҐ",
        KeyEvent.KEYCODE_A to "фФ", KeyEvent.KEYCODE_S to "іІ", KeyEvent.KEYCODE_D to "вВ", KeyEvent.KEYCODE_F to "аА",
        KeyEvent.KEYCODE_G to "пП", KeyEvent.KEYCODE_H to "рР", KeyEvent.KEYCODE_J to "оО", KeyEvent.KEYCODE_K to "лЛ",
        KeyEvent.KEYCODE_L to "дД", KeyEvent.KEYCODE_SEMICOLON to "жЖ", KeyEvent.KEYCODE_APOSTROPHE to "єЄ",
        KeyEvent.KEYCODE_Z to "яЯ", KeyEvent.KEYCODE_X to "чЧ", KeyEvent.KEYCODE_C to "сС", KeyEvent.KEYCODE_V to "мМ",
        KeyEvent.KEYCODE_B to "иИ", KeyEvent.KEYCODE_N to "тТ", KeyEvent.KEYCODE_M to "ьЬ", KeyEvent.KEYCODE_COMMA to "бБ",
        KeyEvent.KEYCODE_PERIOD to "юЮ", KeyEvent.KEYCODE_SLASH to ".,",
    )

    /** US English. */
    private val EN = keys(
        *COMMON, KeyEvent.KEYCODE_GRAVE to "`~",
        KeyEvent.KEYCODE_Q to "qQ", KeyEvent.KEYCODE_W to "wW", KeyEvent.KEYCODE_E to "eE", KeyEvent.KEYCODE_R to "rR",
        KeyEvent.KEYCODE_T to "tT", KeyEvent.KEYCODE_Y to "yY", KeyEvent.KEYCODE_U to "uU", KeyEvent.KEYCODE_I to "iI",
        KeyEvent.KEYCODE_O to "oO", KeyEvent.KEYCODE_P to "pP", KeyEvent.KEYCODE_LEFT_BRACKET to "[{", KeyEvent.KEYCODE_RIGHT_BRACKET to "]}",
        KeyEvent.KEYCODE_BACKSLASH to "\\|",
        KeyEvent.KEYCODE_A to "aA", KeyEvent.KEYCODE_S to "sS", KeyEvent.KEYCODE_D to "dD", KeyEvent.KEYCODE_F to "fF",
        KeyEvent.KEYCODE_G to "gG", KeyEvent.KEYCODE_H to "hH", KeyEvent.KEYCODE_J to "jJ", KeyEvent.KEYCODE_K to "kK",
        KeyEvent.KEYCODE_L to "lL", KeyEvent.KEYCODE_SEMICOLON to ";:", KeyEvent.KEYCODE_APOSTROPHE to "'\"",
        KeyEvent.KEYCODE_Z to "zZ", KeyEvent.KEYCODE_X to "xX", KeyEvent.KEYCODE_C to "cC", KeyEvent.KEYCODE_V to "vV",
        KeyEvent.KEYCODE_B to "bB", KeyEvent.KEYCODE_N to "nN", KeyEvent.KEYCODE_M to "mM", KeyEvent.KEYCODE_COMMA to ",<",
        KeyEvent.KEYCODE_PERIOD to ".>", KeyEvent.KEYCODE_SLASH to "/?",
    )

    private val MAPS = mapOf("uk" to UK, "en" to EN)
}
