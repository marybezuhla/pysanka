package ua.pysanka.voice

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TranscriptTest : FunSpec({
    test("notes on sounds and extra spaces go; the words stay") {
        Transcript.clean(" Привіт, як справи?  [музика] Добре.\n") shouldBe "Привіт, як справи? Добре."
        Transcript.clean("[BLANK_AUDIO]") shouldBe ""
        Transcript.clean(" (сміється) ") shouldBe ""
        Transcript.clean("Так *кашель* звісно") shouldBe "Так звісно"
        Transcript.clean(" ... ") shouldBe ""
        Transcript.clean("Зустріч о 10:30") shouldBe "Зустріч о 10:30"
    }
    test("Whisper's own sample said back for near-silence is dropped; a short greeting is kept") {
        Transcript.clean(" " + Transcript.prompt("uk"), "uk") shouldBe ""
        Transcript.clean("Пишу коротко й зрозуміло, ставлю коми там, де їх вимагає граматика", "uk") shouldBe ""
        Transcript.clean("Добрий день.", "uk") shouldBe "Добрий день."
    }
})
