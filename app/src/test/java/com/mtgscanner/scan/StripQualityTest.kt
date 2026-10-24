package com.mtgscanner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StripQualityTest {
    /** Black and white stripes [period] pixels wide, softened by a box blur of [blur] pixels. */
    private fun stripes(w: Int, h: Int, period: Int, blur: Int, lo: Int = 20, hi: Int = 230): IntArray {
        val row = IntArray(w) { x -> if ((x / period) % 2 == 0) lo else hi }
        val soft = IntArray(w) { x ->
            var sum = 0; var n = 0
            for (d in -blur..blur) { val i = (x + d).coerceIn(0, w - 1); sum += row[i]; n++ }
            sum / n
        }
        return IntArray(w * h) { soft[it % w] }
    }

    @Test
    fun blurLowersSharpness() {
        val sharp = StripQuality.sharpness(stripes(200, 20, 6, 0), 200, 20)
        val soft = StripQuality.sharpness(stripes(200, 20, 6, 3), 200, 20)
        assertTrue("$sharp vs $soft", soft < sharp * 0.5)
    }

    @Test
    fun darknessAloneIsNotBlur() {
        val bright = StripQuality.sharpness(stripes(200, 20, 6, 0, 20, 230), 200, 20)
        val dim = StripQuality.sharpness(stripes(200, 20, 6, 0, 10, 90), 200, 20)
        assertTrue("$bright vs $dim", dim > bright * 0.7)
    }

    @Test
    fun stretchesFlatStrips() {
        val flat = stripes(100, 4, 5, 0, 90, 150)
        val (lo, hi) = StripQuality.levels(flat)
        assertTrue(StripQuality.flat(lo, hi))
        val out = StripQuality.stretch(flat, lo, hi)
        assertEquals(0, out.min())
        assertEquals(255, out.max())
        val (lo2, hi2) = StripQuality.levels(stripes(100, 4, 5, 0, 10, 240))
        assertFalse(StripQuality.flat(lo2, hi2))
    }

    @Test
    fun trackerSkipsOnlyClearlyBlurryFrames() {
        val t = StripQuality.Tracker()
        assertFalse(t.blurry(1.0))
        assertFalse(t.blurry(0.8))
        assertTrue(t.blurry(0.3))
        // The best decays on each skip, so a dimmer scene gets through in the end.
        var skipped = 0
        while (t.blurry(0.3)) skipped++
        assertTrue(skipped < 10)
        t.reset()
        assertFalse(t.blurry(0.1))
    }
}
