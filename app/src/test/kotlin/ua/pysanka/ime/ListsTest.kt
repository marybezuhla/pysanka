package ua.pysanka.ime

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import java.util.Locale

class ListsTest : FunSpec({
    test("an item at the start of the line, taken away when chosen again") {
        Lists.apply("хліб", Lists.Kind.BULLET, false, null) shouldBe "• хліб"
        Lists.apply("• хліб", Lists.Kind.BULLET, false, null) shouldBe "хліб"
        Lists.apply("  молоко", Lists.Kind.TODO, false, null) shouldBe "  ☐ молоко"
        Lists.apply("• хліб", Lists.Kind.NUMBER, false, null) shouldBe "1. хліб"
    }

    test("a to-do is ticked and unticked") {
        Lists.apply("☐ подзвонити", Lists.Kind.TODO, false, null) shouldBe "☑ подзвонити"
        Lists.apply("☑ подзвонити", Lists.Kind.TODO, false, null) shouldBe "☐ подзвонити"
        Lists.apply("- [ ] call", Lists.Kind.TODO, true, null) shouldBe "- [x] call"
        Lists.apply("- [x] call", Lists.Kind.TODO, true, null) shouldBe "- [ ] call"
    }

    test("numbers follow the line before") {
        Lists.apply("", Lists.Kind.NUMBER, false, "2. молоко") shouldBe "3. "
        Lists.apply("", Lists.Kind.NUMBER, false, "просто текст") shouldBe "1. "
    }

    test("the next item after Enter") {
        val md = Lists.parse("- [x] call", true)!!
        Lists.next(md, true) shouldBe "- [ ] "
        Lists.next(Lists.parse("9) дев'ять", false)!!, false) shouldBe "10) "
        Lists.next(Lists.parse("  • хліб", false)!!, false) shouldBe "  • "
        Lists.next(Lists.parse("☑ готово", false)!!, false) shouldBe "☐ "
    }

    test("a dash is a list only in Markdown") {
        Lists.parse("- Привіт, — сказала вона", false) shouldBe null
        Lists.parse("- пункт", true)?.kind shouldBe Lists.Kind.BULLET
        Lists.parse("2026 рік", false) shouldBe null
    }

    test("the date and time in the keyboard's language") {
        val at = LocalDateTime.of(2026, 11, 3, 14, 5)
        Lists.now(Lists.When.TIME, Locale.forLanguageTag("uk"), at) shouldBe "14:05"
        Lists.now(Lists.When.DATE, Locale.forLanguageTag("uk"), at).startsWith("3 листопада 2026") shouldBe true
        Lists.now(Lists.When.DATE, Locale.forLanguageTag("pl"), at).startsWith("3 listopada 2026") shouldBe true
    }
})
