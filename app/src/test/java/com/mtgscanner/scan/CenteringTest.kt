package com.mtgscanner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CenteringTest {
    private val w = CenteringCheck.W
    private val h = CenteringCheck.H

    /** A black-bordered card on a light table, drawn with the outline margin, with these border widths in pixels. */
    private fun card(left: Int, right: Int, top: Int, bottom: Int): IntArray {
        val m = CenteringCheck.MARGIN
        val cardW = w / (1 + 2 * m)
        val cardH = h / (1 + 2 * m)
        val x0 = (m * cardW).toInt(); val x1 = (w - m * cardW).toInt()
        val y0 = (m * cardH).toInt(); val y1 = (h - m * cardH).toInt()
        return IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            when {
                x < x0 || x >= x1 || y < y0 || y >= y1 -> 200
                x < x0 + left || x >= x1 - right || y < y0 + top || y >= y1 - bottom -> 25
                else -> 150
            }
        }
    }

    @Test fun measuresOffCentreBorders() {
        val c = CenteringCheck.measure(card(30, 20, 24, 24), w, h)
        assertNotNull(c)
        assertEquals(30f, c!!.left, 2f)
        assertEquals(20f, c.right, 2f)
        assertEquals("60/40 · 50/50", c.label)
    }

    @Test fun noBorderNoReading() {
        val flat = IntArray(w * h) { 120 }
        assertNull(CenteringCheck.measure(flat, w, h))
    }

    @Test fun medianOfReadings() {
        val m = Centering.median(listOf(Centering(1f, 1f, 1f, 1f), Centering(3f, 1f, 2f, 2f), Centering(9f, 1f, 2f, 2f)))
        assertEquals(3f, m!!.left)
        assertNull(Centering.median(emptyList()))
    }
}
