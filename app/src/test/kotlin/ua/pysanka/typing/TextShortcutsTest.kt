package ua.pysanka.typing

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TextShortcutsTest : FunSpec({
    test("a list survives being written and read") {
        val list = listOf(
            TextShortcuts.Shortcut("дд", "Добрий день!"),
            TextShortcuts.Shortcut("адр", "вул. Вигадана, 1\nКиїв 🌻"),
        )
        TextShortcuts.decode(TextShortcuts.encode(list)) shouldBe list
    }
    test("a shortcut is one word of letters and digits") {
        TextShortcuts.isValidShortcut("дд") shouldBe true
        TextShortcuts.isValidShortcut("gm2") shouldBe true
        TextShortcuts.isValidShortcut("д д") shouldBe false
        TextShortcuts.isValidShortcut("") shouldBe false
        TextShortcuts.isValidShortcut("a".repeat(TextShortcuts.MAX_SHORTCUT + 1)) shouldBe false
    }
})
