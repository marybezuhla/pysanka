package ua.pysanka.typing

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class WordSuggesterTest : FunSpec({
    val s = WordSuggester(
        mapOf("привіт" to 200, "привести" to 120, "зустріч" to 150, "лікаря" to 100, "четвер" to 140, "четверо" to 60, "п'ять" to 90),
        KeyNeighbours.forLanguage("uk"),
    )
    test("completions by frequency, the case of what was typed kept") {
        s.suggest("прив", 2).map { it.word } shouldBe listOf("привіт", "привести")
        s.suggest("Прив", 1).map { it.word } shouldBe listOf("Привіт")
        s.suggest("ПРИВ", 1).map { it.word } shouldBe listOf("ПРИВІТ")
    }
    test("a clear typing error is corrected by itself") {
        s.suggest("зустрч", 3).first() shouldBe WordSuggester.Suggestion("зустріч", autoCorrect = true)
        s.suggest("лікря", 3).first() shouldBe WordSuggester.Suggestion("лікаря", autoCorrect = true)
        s.suggest("привіи", 3).first() shouldBe WordSuggester.Suggestion("привіт", autoCorrect = true)
    }
    test("a known word and a short one are never changed") {
        s.suggest("четвер", 3).none { it.autoCorrect } shouldBe true
        s.suggest("пр", 3).none { it.autoCorrect } shouldBe true
    }
    test("words with an apostrophe") {
        s.suggest("п'я", 1).map { it.word } shouldBe listOf("п'ять")
    }

    test("one spelling of a word with «ё», and no swear words unless typed in full") {
        val ru = WordSuggester(mapOf("еще" to 187, "ещё" to 178, "сука" to 90, "сумка" to 80), KeyNeighbours.forLanguage("ru"))
        ru.suggest("ещ", 3).map { it.word } shouldBe listOf("еще")
        ru.suggest("су", 3).map { it.word } shouldBe listOf("сумка")
        ru.suggest("сука", 3).map { it.word }.first() shouldBe "сука"
        ru.suggest("су", 3, blockOffensive = false).map { it.word } shouldBe listOf("сука", "сумка")
    }
})
