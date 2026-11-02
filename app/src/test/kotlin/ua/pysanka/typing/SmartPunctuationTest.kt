package ua.pysanka.typing

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SmartPunctuationTest : FunSpec({
    test("Ukrainian quotes open after a space and close after a word") {
        SmartPunctuation.of("\"", "uk", null) shouldBe "«"
        SmartPunctuation.of("\"", "uk", ' ') shouldBe "«"
        SmartPunctuation.of("\"", "uk", 'а') shouldBe "»"
        SmartPunctuation.of("\"", "uk", '(') shouldBe "«"
    }
    test("English quotes") {
        SmartPunctuation.of("\"", "en", ' ') shouldBe "“"
        SmartPunctuation.of("\"", "en", 'd') shouldBe "”"
    }
    test("the Ukrainian apostrophe only after a letter; English keeps its own") {
        SmartPunctuation.of("'", "uk", 'м') shouldBe "ʼ"
        SmartPunctuation.of("'", "uk", ' ') shouldBe "'"
        SmartPunctuation.of("'", "en", 'n') shouldBe "'"
    }
    test("other languages and characters are left alone") {
        SmartPunctuation.of("\"", "de", ' ') shouldBe "\""
        SmartPunctuation.of(".", "uk", 'а') shouldBe "."
    }
})
