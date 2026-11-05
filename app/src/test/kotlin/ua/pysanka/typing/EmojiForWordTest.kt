package ua.pysanka.typing

import dev.patrickgold.florisboard.ime.media.emoji.Emoji
import dev.patrickgold.florisboard.ime.media.emoji.pysankaEmojiFor
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File

class EmojiForWordTest : FunSpec({
    val coffee = Emoji("☕", "гарячий напій", listOf("кава", "кафе", "чай"))
    val heart = Emoji("❤️", "червоне серце", listOf("кохання", "серце"))
    val cake = Emoji("🎂", "торт на день народження", listOf("день народження", "торт", "свято"))
    val all = listOf(coffee, heart, cake)

    test("a word that names an emoji") {
        pysankaEmojiFor(all, "кава", 3) shouldBe listOf(coffee)
        pysankaEmojiFor(all, "Серце", 3) shouldBe listOf(heart)
    }
    test("little words offer none, and the more specific emoji comes first") {
        val sick = Emoji("🤮", "обличчя, що блює", listOf("блювання", "мені погано", "нудить"))
        val nest = Emoji("🪹", "порожнє гніздо", listOf("гніздечко", "гніздо", "гілка", "дім", "порожнє гніздо"))
        val house = Emoji("🏠", "будинок", listOf("будинок", "будівля", "дім"))
        pysankaEmojiFor(listOf(sick), "мені", 3) shouldBe emptyList()
        pysankaEmojiFor(listOf(nest, house), "дім", 3) shouldBe listOf(house, nest)
        pysankaEmojiFor(listOf(nest, house), "будинок", 3) shouldBe listOf(house)
    }
    test("a beginning: only from four letters, and after whole words") {
        pysankaEmojiFor(all, "кав", 3) shouldBe emptyList()
        pysankaEmojiFor(all, "наро", 3) shouldBe listOf(cake)
        pysankaEmojiFor(all, "при", 3) shouldBe emptyList()
    }
    test("Ukrainian names come with the keyboard, in the same order as English") {
        val dir = File("src/main/assets/ime/media/emoji")
        val uk = File(dir, "uk.txt").readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        val en = File(dir, "en.txt").readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        uk.map { it.substringBefore(';') } shouldBe en.map { it.substringBefore(';') }
        uk.first { it.startsWith("☕;") }.contains("кава") shouldBe true
        // Russian too, for people moving to Ukrainian
        val ru = File(dir, "ru.txt").readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        ru.map { it.substringBefore(';') } shouldBe en.map { it.substringBefore(';') }
        ru.first { it.startsWith("☕;") }.contains("кофе") shouldBe true
    }
})
