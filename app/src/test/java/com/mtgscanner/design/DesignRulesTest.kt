package com.mtgscanner.design

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
}
