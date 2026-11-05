package com.mtgscanner.design

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The rules in design/Design.md that a build can check. */
class DesignRulesTest {
    private val sources = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()
    private val span = Regex("""(?:padding|spacedBy|PaddingValues|Spacer\(Modifier\.(?:height|width))\(([^()]*)\)""")
    private val literal = Regex("""\b(\d+)\.dp\b""")

    /** Spacing comes from Space.*; only 0 and 1 (hairlines) and layout sizes of 40dp and up stay literal. */
    @Test
    fun spacingIsOnTheGrid() {
        val bad = sources.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                val off = span.findAll(line).flatMap { literal.findAll(it.groupValues[1]) }
                    .map { it.groupValues[1].toInt() }.filter { it > 1 && it < 40 }.toList()
                if (off.isEmpty()) null else "${f.path}:${i + 1} ${off.joinToString { "$it.dp" }} (use Space.*)"
            }
        }
        assertTrue("Off-grid spacing:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    /** Buttons and backgrounds are flat; gradients are only scrims over art or camera. */
    @Test
    fun noGradientButtons() {
        val bad = sources.filter { "AccentGradient" in it.readText() }.map { it.path }
        assertTrue("Gradient accent used in: $bad", bad.isEmpty())
    }

    private val tokens by lazy { Json.parseToJsonElement(File("../design/design-tokens.json").readText()).jsonObject }
    private fun colors(mode: String): Map<String, String> =
        tokens["color"]!!.jsonObject[mode]!!.jsonObject.mapValues { it.value.jsonPrimitive.content }.filterValues { it.startsWith("#") }

    /** design-tokens.json is the source of truth: every color in it must be the one the app uses. */
    @Test
    fun tokensMatchTheCode() {
        val code = File("src/main/java/com/mtgscanner/ui/theme/Theme.kt").readText() +
            File("src/main/java/com/mtgscanner/ui/theme/ThemeSettings.kt").readText()
        val missing = listOf("dark", "light").flatMap { mode ->
            colors(mode).filter { (_, hex) -> hex.length == 7 && !code.contains("0xFF" + hex.drop(1), ignoreCase = true) }
                .map { (k, hex) -> "$mode.$k $hex" }
        }
        assertTrue("Colors in design-tokens.json the code doesn't use: $missing", missing.isEmpty())
    }

    /** WCAG AA: primary and muted text read at 4.5:1 on every surface, in both modes. */
    @Test
    fun textContrast() {
        val bad = listOf("dark", "light").flatMap { mode ->
            val c = colors(mode)
            listOf("text", "textMuted").flatMap { fg ->
                listOf("bg", "surface", "surface2", "surfaceOffset").mapNotNull { bg ->
                    val r = ratio(c[fg]!!, c[bg]!!)
                    if (r < 4.5) "$mode $fg on $bg: ${"%.2f".format(r)}" else null
                }
            }
        }
        assertTrue("Text below 4.5:1: $bad", bad.isEmpty())
    }

    private fun lum(hex: String): Double {
        fun ch(i: Int): Double {
            val v = hex.substring(1 + 2 * i, 3 + 2 * i).toInt(16) / 255.0
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * ch(0) + 0.7152 * ch(1) + 0.0722 * ch(2)
    }
    private fun ratio(a: String, b: String): Double {
        val x = lum(a) + 0.05; val y = lum(b) + 0.05
        return maxOf(x, y) / minOf(x, y)
    }
}
