package com.mtgscanner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SetSymbolTest {
    /** Whether (x, y), in a unit square, is inside the shape. */
    private val shapes: Map<String, (Float, Float) -> Boolean> = mapOf(
        "circle" to { x, y -> (x - 0.5f) * (x - 0.5f) + (y - 0.5f) * (y - 0.5f) < 0.2f },
        "triangle" to { x, y -> y > 0.1f && y < 0.9f && kotlin.math.abs(x - 0.5f) < (y - 0.1f) * 0.55f },
        "cross" to { x, y -> (kotlin.math.abs(x - 0.5f) < 0.12f || kotlin.math.abs(y - 0.5f) < 0.12f) && x in 0.1f..0.9f && y in 0.1f..0.9f },
        "ring" to { x, y -> val d = (x - 0.5f) * (x - 0.5f) + (y - 0.5f) * (y - 0.5f); d < 0.2f && d > 0.07f },
    )

    /** Scryfall's icon drawn black on white, [size] square. */
    private fun icon(shape: (Float, Float) -> Boolean, size: Int = 64): IntArray =
        IntArray(size * size) { i ->
            val x = (i % size) / size.toFloat()
            val y = (i / size) / size.toFloat()
            if (shape(x, y)) 0 else 255
        }

    /**
     * The type line on a card: colored bar with frame lines along the top and bottom, the symbol drawn smaller
     * and off-center in [color] with a dark outline, and a little noise.
     */
    private fun onCard(shape: (Float, Float) -> Boolean, w: Int = 48, h: Int = 44, color: Int = 200, seed: Int = 1): IntArray {
        val r = Random(seed)
        val px = IntArray(w * h) { 150 + r.nextInt(-4, 5) }
        for (x in 0 until w) { px[x] = 20; px[w + x] = 20; px[(h - 1) * w + x] = 20; px[(h - 2) * w + x] = 20 }
        val s = 26
        val ox = 14
        val oy = 9
        for (y in 0 until s) for (x in 0 until s) {
            val fx = x / s.toFloat()
            val fy = y / s.toFloat()
            if (!shape(fx, fy)) continue
            val edge = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1).any { (dx, dy) -> !shape((x + dx) / s.toFloat(), (y + dy) / s.toFloat()) }
            px[(oy + y) * w + ox + x] = if (edge) 15 else color
        }
        return px
    }

    private val refs = shapes.map { (k, f) -> k to SetSymbol.fingerprint(icon(f), 64, 64)!! }

    @Test fun findsEachShape() {
        for ((name, f) in shapes) {
            for (color in listOf(30, 200, 240)) {
                val print = SetSymbol.fingerprint(onCard(f, color = color, seed = color), 48, 44)
                assertNotNull(name, print)
                assertEquals("$name in $color", name, SetSymbol.pick(print!!, refs))
            }
        }
    }

    @Test fun sameShapeScoresHigherThanOthers() {
        val circle = SetSymbol.fingerprint(onCard(shapes.getValue("circle")), 48, 44)!!
        val self = SetSymbol.similarity(circle, refs.first { it.first == "circle" }.second)
        val other = SetSymbol.similarity(circle, refs.first { it.first == "cross" }.second)
        assertTrue("$self vs $other", self > other + 0.1f)
    }

    @Test fun nothingThereGivesNothing() {
        val flat = IntArray(48 * 44) { 150 }
        assertNull(SetSymbol.fingerprint(flat, 48, 44))
        // Only frame lines: no symbol.
        val lines = IntArray(48 * 44) { i -> if (i / 48 < 2 || i / 48 > 41) 20 else 150 }
        assertNull(SetSymbol.fingerprint(lines, 48, 44))
    }

    @Test fun unclearPicksAreIgnored() {
        val circle = SetSymbol.fingerprint(icon(shapes.getValue("circle")), 64, 64)!!
        // Two sets with the same icon: no clear lead, so no answer.
        assertNull(SetSymbol.pick(circle, listOf("a" to circle, "b" to circle)))
        assertEquals("a", SetSymbol.pick(circle, listOf("a" to circle)))
    }

    @Test fun bytesRoundTrip() {
        val p = refs.first().second
        val back = SymbolPrint.fromBytes(p.toBytes())!!
        assertTrue(SetSymbol.similarity(p, back) > 0.99f)
        assertNull(SymbolPrint.fromBytes(ByteArray(3)))
    }
}
