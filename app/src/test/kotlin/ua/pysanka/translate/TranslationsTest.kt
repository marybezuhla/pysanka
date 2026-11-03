package ua.pysanka.translate

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import java.io.File

class TranslationsTest : FunSpec({
    // The dictionaries of the app, read from the sources
    val src = Translations.Source { name -> File("src/main/assets/ime/translate/$name").takeIf { it.exists() }?.readText() }
    fun words(text: String, from: String, to: String, hint: String = "uk") =
        Translations.translate(src, text, from, to, hint).results.map { it.word }

    test("sorted lines are found by their key") {
        val lines = SortedLines("a\t1\nb c\t2\nd\t\n")
        lines.find("a") shouldBe "1"
        lines.find("b c") shouldBe "2"
        lines.find("d") shouldBe ""
        lines.find("c") shouldBe null
        lines.find("") shouldBe null
        SortedLines("").find("a") shouldBe null
        SortedLines("x\t9").find("x") shouldBe "9"
    }

    test("the key: small letters, no stress marks on Cyrillic, Latin marks kept") {
        Translations.key(" «Ключа́», ") shouldBe "ключа"
        Translations.key("Szép") shouldBe "szép"
        Translations.key("м’ята") shouldBe "м'ята"
    }

    test("a form is found by its dictionary form") {
        Translations.lemmas(src, "uk", "ключа") shouldContain "ключ"
        Translations.lemmas(src, "pl", "klucza") shouldContain "klucz"
        Translations.lemmas(src, "en", "keys") shouldContain "key"
    }

    test("Ukrainian to and from the other languages") {
        words("ключа", "uk", "en") shouldContain "key"
        words("кіт", "uk", "de") shouldContain "Katze"
        words("klucza", "pl", "uk") shouldContain "ключ"
        words("Hund", "de", "uk") shouldContain "собака"
        words("собаки", "ru", "uk") shouldContain "собака"
        words("dziękuję", "pl", "uk") shouldContain "дякую"
        words("szép", "hu", "uk").isNotEmpty() shouldBe true
        words("pes", "sk", "uk") shouldContain "пес"
        words("mañana", "es", "uk") shouldContain "завтра"
    }

    test("a stray meaning gives no translation") {
        // «собачка» is also what «@» is called
        words("собака", "ru", "uk") shouldNotContain "вухо"
    }

    test("the language is found from the word and its letters") {
        Translations.detect(src, "klucza", listOf("uk", "pl")) shouldBe "pl"
        Translations.detect(src, "Schlüssel", listOf("uk")) shouldBe "de"
        Translations.detect(src, "їжак", listOf("ru", "uk")) shouldBe "uk"
        Translations.detect(src, "сабака", listOf("uk", "be")) shouldBe "be"
    }

    test("English endings") {
        Translations.englishStems("running") shouldContain "run"
        Translations.englishStems("cities") shouldContain "city"
    }
})
