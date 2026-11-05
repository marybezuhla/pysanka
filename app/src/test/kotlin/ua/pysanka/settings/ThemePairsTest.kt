package ua.pysanka.settings

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File

class ThemePairsTest : FunSpec({
    test("each theme brings its pair, both ways, as in the other apps") {
        val pairs = mapOf(
            "floris_day_my" to "floris_night_my", "nebo" to "zoriana_nich", "kalyna" to "mak",
            "linen" to "chornozem", "visk" to "smereka", "glyna" to "zhar",
        )
        for ((day, night) in pairs) {
            pairOf(day)?.componentId shouldBe night
            pairOf(night)?.componentId shouldBe day
        }
        pairOf("someone_elses") shouldBe null
    }

    test("every own theme has a stylesheet") {
        val dir = File("src/main/assets/ime/theme/org.florisboard.themes/stylesheets")
        for (id in listOf("nebo", "kalyna", "linen", "visk", "glyna", "zoriana_nich", "mak", "chornozem", "smereka", "zhar")) {
            File(dir, "$id.json").isFile shouldBe true
        }
    }
})
