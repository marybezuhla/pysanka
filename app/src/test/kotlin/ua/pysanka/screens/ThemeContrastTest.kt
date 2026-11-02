package ua.pysanka.screens

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.math.pow

/**
 * Pysanka: the letters on the keys of the built-in themes are easy to read: text at least 4.5:1
 * against its key (WCAG AA), large text in the long-press popup and icons at least 3:1.
 */
class ThemeContrastTest : FunSpec({
    val dir = File("src/main/assets/ime/theme/org.florisboard.themes/stylesheets")

    fun rgba(s: String): DoubleArray {
        val h = s.trim().removePrefix("#")
        val a = if (h.length == 8) h.substring(6, 8).toInt(16) / 255.0 else 1.0
        return doubleArrayOf(h.substring(0, 2).toInt(16) / 255.0, h.substring(2, 4).toInt(16) / 255.0, h.substring(4, 6).toInt(16) / 255.0, a)
    }

    fun over(fg: DoubleArray, bg: DoubleArray) = DoubleArray(3) { fg[it] * fg[3] + bg[it] * (1 - fg[3]) } + 1.0

    fun lum(c: DoubleArray): Double {
        fun f(x: Double) = if (x <= 0.03928) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4)
        return 0.2126 * f(c[0]) + 0.7152 * f(c[1]) + 0.0722 * f(c[2])
    }

    fun ratio(a: DoubleArray, b: DoubleArray): Double {
        val (hi, lo) = listOf(lum(a), lum(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    test("all six themes are checked") {
        dir.listFiles { f -> f.extension == "json" }!!.size shouldBe 6
    }

    for (file in dir.listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }) {
        test(file.name) {
            val sheet = Json.parseToJsonElement(file.readText()).jsonObject
            val defines = sheet["@defines"]!!.jsonObject
            fun value(rule: String, prop: String): String? {
                var v = (sheet[rule] as? JsonObject)?.get(prop)?.jsonPrimitive?.content ?: return null
                while (v.startsWith("var(")) v = defines[v.removePrefix("var(").removeSuffix(")")]!!.jsonPrimitive.content
                return v
            }
            val window = rgba(value("window", "background")!!)
            fun check(rule: String, min: Double, bgRule: String = rule, fgRule: String = rule) {
                val fg = value(rule, "foreground") ?: value(fgRule, "foreground")!!
                val bgValue = value(bgRule, "background")
                val bg = if (bgValue == null || bgValue == "transparent") window else over(rgba(bgValue), window)
                withClue(rule) { ratio(over(rgba(fg), bg), bg) shouldBeGreaterThanOrEqual min }
            }
            check("key", 4.5)
            check("key[code=32]", 4.5)
            check("key-hint", 4.5, bgRule = "key")
            check("key[code=10]", 4.5)
            check("key[code=10]:pressed", 4.5)
            check("key-popup-box", 4.5)
            check("key-popup-element:focus", 3.0, fgRule = "key-popup-box")
            check("key[code=-11][shiftstate=`caps_lock`]", 3.0, bgRule = "key")
            check("clipboard-filter-chip[state=`active`]", 4.5)
            check("smartbar-actions-overflow-customize-button", 4.5)
        }
    }
})
