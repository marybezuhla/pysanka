package ua.pysanka.ai

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class StylePromptTest : FunSpec({
    test("the samples go to the model in order, up to the limit") {
        val long = "а".repeat(StyleSamples.MAX_CHARS)
        StyleSamples.forPrompt(listOf("перший", "другий")) shouldBe listOf("перший", "другий")
        StyleSamples.forPrompt(List(5) { long }).sumOf { it.length } shouldBe StyleSamples.PROMPT_CHARS / long.length * long.length
        StyleSamples.cut("а".repeat(StyleSamples.MAX_CHARS - 1) + "😀").length shouldBe StyleSamples.MAX_CHARS - 1
    }

    test("«In my style» carries the samples and the rules; other actions do not") {
        val style = listOf("Привіт-привіт! Буду о 10, не запізнюйся :)", "Ок, домовились")
        val prompt = AiPrompts.system(AiAction.MY_STYLE, AiLanguage.EN, "Надішліть звіт до п'ятниці.", style)
        prompt shouldContain "-----\nПривіт-привіт! Буду о 10, не запізнюйся :)\n-----\nОк, домовились\n-----"
        prompt shouldContain "Never copy facts"
        prompt shouldContain "Write in Ukrainian"
        AiPrompts.system(AiAction.FIX, AiLanguage.EN, "Текст", style) shouldNotContain "Привіт-привіт"
    }

    test("a post has as many emoji as chosen; other actions do not mention them") {
        AiPrompts.system(AiAction.POST, AiLanguage.UK, "Текст", emoji = 0) shouldContain "no emoji at all"
        AiPrompts.system(AiAction.VERSIONS, AiLanguage.UK, "Текст", emoji = 3) shouldContain "four to six emoji"
        AiPrompts.system(AiAction.POST, AiLanguage.UK, "Текст") shouldContain "two or three emoji"
        AiPrompts.system(AiAction.FIX, AiLanguage.UK, "Текст", emoji = 0) shouldNotContain "emoji at all"
    }
})
