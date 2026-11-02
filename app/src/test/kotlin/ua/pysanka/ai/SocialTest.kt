package ua.pysanka.ai

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SocialTest : FunSpec({
    test("a post that fits stays as it is") {
        ThreadSplit.split("  Коротко.  ", 280) shouldBe listOf("Коротко.")
    }

    test("whole sentences per part, each numbered, none over the limit") {
        val s = "Перше речення про погоду. Друге речення трохи довше за перше! Третє? Четверте речення в кінці."
        val parts = ThreadSplit.split(s, 40)
        parts.forEach { (SocialApps.length(it) <= 40) shouldBe true }
        parts.last().endsWith(" ${parts.size}/${parts.size}") shouldBe true
        parts.first() shouldBe "Перше речення про погоду. 1/${parts.size}"
        parts.joinToString(" ") { it.substringBeforeLast(' ') } shouldBe s
    }

    test("a sentence longer than a part goes by words; a word longer than a part is cut") {
        val parts = ThreadSplit.split("слово ".repeat(30).trim() + " " + "а".repeat(50), 30)
        parts.forEach { (SocialApps.length(it) <= 30) shouldBe true }
        parts.joinToString("") { it.substringBeforeLast(' ').replace(" ", "") } shouldBe "слово".repeat(30) + "а".repeat(50)
    }

    test("emoji count once as seen and are never cut") {
        SocialApps.length("👍🏽 так 🇺🇦") shouldBe 7
        Graphemes.count("👨‍👩‍👧") shouldBe 1
        Graphemes.count("и\u0306") shouldBe 1
        val parts = ThreadSplit.split("👍🏽".repeat(40), 20)
        parts.forEach { p -> p.substringBeforeLast(' ').chunked(4).all { it == "👍🏽" } shouldBe true }
        ThreadSplit.split("слово ".repeat(100), 3).isNotEmpty() shouldBe true
    }

    test("the apps known by their package") {
        SocialApps.of("com.twitter.android")?.limit shouldBe 280
        SocialApps.of("org.telegram.messenger") shouldBe null
    }
})
