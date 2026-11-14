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
        // The always-dark and true-black colors count too. Pure black is written Color.Black in the code.
        val missing = listOf("dark", "light", "trueBlack", "alwaysDark").flatMap { mode ->
            colors(mode).filter { (_, hex) -> hex.length == 7 && hex != "#000000" && !code.contains("0xFF" + hex.drop(1), ignoreCase = true) }
                .map { (k, hex) -> "$mode.$k $hex" }
        }
        assertTrue("Colors in design-tokens.json the code doesn't use: $missing", missing.isEmpty())
    }

    /** The spacing steps, corners, animation lengths and the easing curve in design-tokens.json are the ones Theme.kt uses. */
    @Test
    fun spacingCornersAndMotionMatchTheCode() {
        val theme = File("src/main/java/com/mtgscanner/ui/theme/Theme.kt").readText()
        fun group(name: String) = tokens[name]!!.jsonObject.filterKeys { !it.startsWith("_") }.mapValues { it.value.jsonPrimitive.content }
        val motion = group("motion")
        val radius = group("radius")
        val want = group("spacing").filterKeys { it != "gutter" }.map { (k, v) -> "val $k = ${v.removeSuffix("dp")}.dp" } +
            listOf("fast", "base", "slow").map { "const val $it = ${motion.getValue(it).removeSuffix("ms")}" } +
            "val Small = ${radius.getValue("sm").removeSuffix("dp")}.dp" +
            "val Medium = ${radius.getValue("md").removeSuffix("dp")}.dp" +
            // "cubic-bezier(0.22, 1, 0.36, 1)" is CubicBezierEasing(0.22f, 1f, 0.36f, 1f) in the code.
            ("CubicBezierEasing(" + motion.getValue("easing").substringAfter("(").substringBefore(")").split(",").joinToString(", ") { it.trim() + "f" } + ")")
        val missing = want.filter { it !in theme }
        assertTrue("design-tokens.json and Theme.kt disagree. Theme.kt has no: $missing", missing.isEmpty())
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

    /** Motion.ease is the only curve: no second cubic-bezier, and none of Compose's named easings. */
    @Test
    fun oneEasingCurve() {
        val other = Regex("""\bCubicBezierEasing\(|\bLinearOutSlowInEasing\b|\bFastOutLinearInEasing\b|\bEase(In|Out)\w*""")
        val bad = sources.filter { it.parentFile.name != "theme" && other.containsMatchIn(it.readText()) }.map { it.path }
        assertTrue("Use Motion.ease in: $bad", bad.isEmpty())
        val curves = Regex("""\bCubicBezierEasing\(""").findAll(File("src/main/java/com/mtgscanner/ui/theme/Theme.kt").readText()).count()
        assertTrue("Theme.kt defines $curves easing curves; there is one, Motion.ease", curves == 1)
    }

    /**
     * The spacing rule again, for values the one-line check above can't see: inside an if or other brackets, in a
     * call split over several lines, and in Spacer(Modifier.size(...)). The files in [knownOffGrid] still have such
     * values (found 2026-11-14); take a file off the list when it is fixed, and add none.
     */
    private val knownOffGrid = setOf(
        "BoxPlanSheet.kt", "BrowseRows.kt", "PlainRows.kt", "HomeScreen.kt", "FilterSheet.kt", "SettingsScreen.kt",
    )

    @Test
    fun nestedSpacingIsOnTheGrid() {
        val call = Regex("""\b(?:padding|spacedBy|PaddingValues|Spacer\(Modifier\.(?:height|width|size))\(""")
        val bad = sources.filter { it.name !in knownOffGrid }.flatMap { f ->
            val t = f.readText()
            call.findAll(t).mapNotNull { m ->
                // The call's arguments, up to the bracket that closes it.
                val start = m.range.last + 1
                var end = start
                var depth = 1
                while (end < t.length && depth > 0) {
                    if (t[end] == '(') depth++ else if (t[end] == ')') depth--
                    end++
                }
                val off = literal.findAll(t.substring(start, end)).map { it.groupValues[1].toInt() }.filter { it > 1 && it < 40 }.toList()
                if (off.isEmpty()) null
                else "${f.path}:${t.substring(0, start).count { it == '\n' } + 1} ${off.joinToString { "$it.dp" }} (use Space.*)"
            }.toList()
        }
        assertTrue("Off-grid spacing:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    /** Two fonts, fixed: font files are loaded only by the theme and the mana and set symbol fonts. */
    @Test
    fun noExtraFonts() {
        val symbols = setOf("ManaFont.kt", "SetSymbolFont.kt")
        val loads = Regex("""\bFontFamily\(|\bFont\(R\.font""")
        val bad = sources.filter { it.parentFile.name != "theme" && it.name !in symbols && loads.containsMatchIn(it.readText()) }.map { it.path }
        assertTrue("A font loaded outside ui/theme: $bad", bad.isEmpty())
    }

    /** No emoji anywhere in the code. (Marks like the foil's ✦ are plain symbols, not emoji.) */
    @Test
    fun noEmoji() {
        val emoji = Regex("""[\x{1F000}-\x{1FAFF}\x{FE0F}]""")
        val bad = sources.filter { emoji.containsMatchIn(it.readText()) }.map { it.path }
        assertTrue("Emoji in: $bad", bad.isEmpty())
    }

    /**
     * Copy outside Text(...) too (snackbars, labels built in view models): no exclamation mark closing a string, no
     * filler ("Please", "Successfully", "Are you sure", "Oops") and none of the buzzwords Design.md lists.
     */
    @Test
    fun plainCopy() {
        val shout = Regex(""""[^"\n]*[a-z]!"""")
        val filler = Regex(""""[^"\n]*\b(?:[Pp]lease|[Ss]uccessfully|Are you sure|Oops|Whoops|[Uu]nlock|[Ss]eamless|[Ee]levate|[Ll]everage|[Ee]mpower)\b[^"\n]*"""")
        val bad = ui.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                val code = line.trimStart()
                when {
                    code.startsWith("//") || code.startsWith("*") || code.startsWith("/*") -> null
                    shout.containsMatchIn(line) -> "${f.path}:${i + 1} exclamation mark"
                    filler.containsMatchIn(line) -> "${f.path}:${i + 1} ${filler.find(line)!!.value.take(60)}"
                    else -> null
                }
            }
        }
        assertTrue("Copy rules:\n" + bad.joinToString("\n"), bad.isEmpty())
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
