package com.mtgscanner.scan

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CardQuadTest {
    private val q = floatArrayOf(0f, 0f, 10f, 0f, 10f, 14f, 0f, 14f) // TL, TR, BR, BL; taller than wide

    @Test fun orderFollowsTheTurn() {
        assertArrayEquals(q, CardQuad.ordered(q, 0), 0f)
        // Turned clockwise by 90, the image's bottom-left is the card's top-left.
        assertArrayEquals(floatArrayOf(0f, 14f, 0f, 0f, 10f, 0f, 10f, 14f), CardQuad.ordered(q, 90), 0f)
        assertArrayEquals(floatArrayOf(10f, 14f, 0f, 14f, 0f, 0f, 10f, 0f), CardQuad.ordered(q, 180), 0f)
        assertArrayEquals(floatArrayOf(10f, 0f, 10f, 14f, 0f, 14f, 0f, 0f), CardQuad.ordered(q, 270), 0f)
    }

    @Test fun onlyTurnsThatStandTheCardTall() {
        assertEquals(listOf(0, 180), CardQuad.uprightTurns(q, 0))
        assertEquals(listOf(180, 0), CardQuad.uprightTurns(q, 180))
        // Last time it was a quarter turn, but this outline is tall: the nearest turn that fits.
        assertEquals(listOf(180, 0), CardQuad.uprightTurns(q, 90))
        val wide = floatArrayOf(0f, 0f, 14f, 0f, 14f, 10f, 0f, 10f)
        assertEquals(listOf(90, 270), CardQuad.uprightTurns(wide, 90))
        assertEquals(listOf(90, 270), CardQuad.uprightTurns(wide, 0))
    }

    @Test fun scaledToImagePixels() {
        assertArrayEquals(floatArrayOf(100f, 50f, 140f, 50f, 140f, 106f, 100f, 106f), CardQuad.scaled(q, 4, 100, 50), 0f)
    }

    /** A light card whose sides lean: the left and right edges drift [lean] pixels over its height. */
    private fun tilted(w: Int, h: Int, l: Int, t: Int, cw: Int, ch: Int, lean: Int): IntArray {
        val px = IntArray(w * h) { 40 }
        for (y in t until t + ch) {
            val shift = (y - t) * lean / ch
            for (x in l + shift until l + cw + shift) if (x in 0 until w) px[y * w + x] = 210
        }
        return px
    }

    @Test fun cornersFollowALeaningCard() {
        val w = 120
        val h = 160
        val px = tilted(w, h, 15, 20, 90, 126, 4)
        val box = OcrBox(17, 20, 107, 146)
        val c = CardQuad.corners(px, w, h, box)
        assertNotNull(c)
        c!!
        // Edges sit between pixels: top-left near (14.5, 19.5), bottom-left near (18.5, 145.5).
        assertTrue("${c.toList()}", abs(c[0] - 14.5f) <= 1f && abs(c[1] - 19.5f) <= 1f)
        assertTrue("${c.toList()}", abs(c[6] - 18.5f) <= 1f && abs(c[7] - 145.5f) <= 1f)
        assertTrue("${c.toList()}", abs(c[2] - 104.5f) <= 1f && abs(c[5] - 145.5f) <= 1f)
    }

    @Test fun lineFitDropsStrays() {
        val pts = (0 until 40).map { it.toDouble() to (2.0 + it * 0.1) } + listOf(5.0 to 30.0, 7.0 to -20.0)
        val (a, b) = CardQuad.fitLine(pts, 42)!!
        assertEquals(0.1, a, 0.01)
        assertEquals(2.0, b, 0.1)
    }
}
