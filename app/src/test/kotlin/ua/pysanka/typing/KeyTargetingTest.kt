package ua.pysanka.typing

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class KeyTargetingTest : FunSpec({
    // Two keys side by side, 100 wide: «о» (0..100) and «р» (100..200)
    val o = KeyTargeting.Target(0f, 0f, 100f, 150f, 'о')
    val r = KeyTargeting.Target(100f, 0f, 200f, 150f, 'р')
    val likelyR = KeyTargeting.Priors("п", mapOf('р' to 0.6, 'о' to 0.1))
    val even = KeyTargeting.Priors("п", mapOf('р' to 0.3, 'о' to 0.3))

    test("a tap well inside a key is that key, however unlikely") {
        KeyTargeting.pick(50f, 75f, listOf(o, r), likelyR) shouldBe 0
        // r is not even near: the keyboard would not offer it
        r.near(50f, 75f) shouldBe false
    }

    test("near the edge the likelier letter takes it; with even odds the closer key") {
        KeyTargeting.pick(95f, 75f, listOf(o, r), likelyR) shouldBe 1
        KeyTargeting.pick(95f, 75f, listOf(o, r), even) shouldBe 0
        r.near(80f, 75f) shouldBe true
        r.near(70f, 75f) shouldBe false
    }

    test("priors count only for the word they were worked out for") {
        KeyTargeting.publish(likelyR)
        (KeyTargeting.current("П") != null) shouldBe true
        KeyTargeting.current("пр") shouldBe null
        KeyTargeting.publish(null)
        KeyTargeting.current("п") shouldBe null
    }

    test("the word list's likely next letters") {
        val s = WordSuggester(mapOf("привіт" to 200, "прохання" to 120, "пароль" to 100, "кіт" to 50))
        val n = s.nextLetters("п")
        (n.getValue('р') > n.getValue('а')) shouldBe true
        (n.values.sum() in 0.999..1.001) shouldBe true
        s.nextLetters("пр").keys shouldBe setOf('и', 'о')
        s.nextLetters("щ") shouldBe emptyMap()
    }

    test("a hand that always taps low is learned and taken off; never more than a third of a key") {
        val c = KeyTargeting.Calibration
        c.reset()
        val key = KeyTargeting.Target(0f, 0f, 100f, 150f, 'о')
        val rnd = java.util.Random(1)
        repeat(400) {
            // Taps around a point 0.2 of a key below the middle
            val (x, y) = c.adjust(50f + rnd.nextGaussian().toFloat() * 15f, 75f + 30f + rnd.nextGaussian().toFloat() * 15f, 100f, 150f)
            c.learn(x, y, key)
        }
        (c.dy in 0.15f..0.25f) shouldBe true
        (kotlin.math.abs(c.dx) < 0.06f) shouldBe true
        repeat(400) { c.learn(50f, 150f, key) }
        (c.dy <= 0.35f) shouldBe true
        c.reset()
    }
})
