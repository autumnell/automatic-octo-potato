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

    // unslop rules (design/unslop, the default design philosophy): type, copy, motion and color checks a build can make.
    private val ui = sources.filter { "/ui/" in it.path.replace('\\', '/') || it.name == "MainActivity.kt" }

    /** Weights stop at 600. Dice faces, label-maker tape and mana symbols draw real objects and keep their bold. */
    @Test
    fun noHeavyWeights() {
        val objects = setOf("DiceRoller.kt", "LabelTape.kt")
        val heavy = Regex("""FontWeight\.(Bold|ExtraBold|Black|W[789]00)""")
        val bad = ui.filter { it.name !in objects && it.parentFile.name != "theme" }.flatMap { f ->
            val lines = f.readLines()
            lines.mapIndexedNotNull { i, line ->
                // A mana symbol drawn as a letter in a colored circle, like the printed symbol.
                val manaSymbol = lines.subList(maxOf(0, i - 3), i + 1).any { "symbolText" in it }
                if (heavy.containsMatchIn(line) && !manaSymbol) "${f.path}:${i + 1}" else null
            }
        }
        assertTrue("Weights over 600 (use Medium or SemiBold):\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    /** No interface text under 11sp. */
    @Test
    fun noTinyText() {
        val tiny = Regex("""fontSize = (\d+(?:\.\d+)?)\.sp""")
        val bad = ui.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                val small = tiny.findAll(line).map { it.groupValues[1].toDouble() }.filter { it < 11 }.toList()
                if (small.isEmpty()) null else "${f.path}:${i + 1} ${small.joinToString()}sp"
            }
        }
        assertTrue("Text under 11sp:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    /** No exclamation marks, and labels, captions and one-sentence notes end without a period. */
    @Test
    fun calmCopy() {
        val shout = Regex("""Text\("[^"]*!"""")
        val period = Regex("""Text\("([^"$]*[a-z])\.["]""")
        val bad = ui.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                val p = period.find(line)?.groupValues?.get(1)
                when {
                    shout.containsMatchIn(line) -> "${f.path}:${i + 1} exclamation mark"
                    p != null && !Regex("""[.?!] [A-Z]""").containsMatchIn(p) -> "${f.path}:${i + 1} trailing period"
                    else -> null
                }
            }
        }
        assertTrue("Copy rules:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    /** No colored glows: shadows are neutral. */
    @Test
    fun noColoredShadows() {
        val bad = sources.filter { Regex("""(ambientColor|spotColor) = Palette\.(Accent|Gold|Success|Danger|Info)""").containsMatchIn(it.readText()) }.map { it.path }
        assertTrue("Colored shadow (glow) in: $bad", bad.isEmpty())
    }

    /** One easing curve (Motion.ease); bouncy springs only for dice, which really bounce. */
    @Test
    fun calmMotion() {
        val bad = sources.filter { f ->
            val t = f.readText()
            "FastOutSlowInEasing" in t || (f.name != "DiceRoller.kt" && Regex("""dampingRatio = (0\.[0-8]|Spring\.DampingRatio(Low|Medium|High)Bouncy)""").containsMatchIn(t))
        }.map { it.path }
        assertTrue("Use Motion.ease and no bounce in: $bad", bad.isEmpty())
    }

    /** A tween with no easing falls back to FastOutSlowIn, a second curve; every tween names Motion.ease (or linear for loops). */
    @Test
    fun everyTweenNamesItsEasing() {
        val call = Regex("""\btween\(([^()]|\([^()]*\))*\)""")
        val bad = sources.flatMap { f ->
            call.findAll(f.readText()).filter { "easing" !in it.value }.map { "${f.name}: ${it.value}" }.toList()
        }
        assertTrue("Give these tweens easing = Motion.ease: $bad", bad.isEmpty())
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
