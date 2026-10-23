package com.mtgscanner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YuvTest {
    private fun r(p: Int) = (p shr 16) and 0xFF
    private fun g(p: Int) = (p shr 8) and 0xFF
    private fun b(p: Int) = p and 0xFF

    @Test fun blackAndWhiteSpanTheRange() {
        assertEquals(0x000000, Yuv.rgb(16, 128, 128))
        assertEquals(0xFFFFFF, Yuv.rgb(235, 128, 128))
        assertEquals(0, Yuv.luma(16))
        assertEquals(255, Yuv.luma(235))
        assertEquals(0, Yuv.luma(0))
        assertEquals(255, Yuv.luma(255))
    }

    @Test fun greyHasNoColour() {
        val p = Yuv.rgb(126, 128, 128)
        assertEquals(r(p), g(p))
        assertEquals(g(p), b(p))
        assertEquals(Yuv.luma(126), r(p))
    }

    @Test fun primariesComeOutRight() {
        // BT.601 limited-range red, green and blue.
        val red = Yuv.rgb(81, 90, 240)
        assertTrue(r(red) >= 250 && g(red) <= 5 && b(red) <= 5)
        val green = Yuv.rgb(145, 54, 34)
        assertTrue(g(green) >= 250 && r(green) <= 5 && b(green) <= 5)
        val blue = Yuv.rgb(41, 240, 110)
        assertTrue(b(blue) >= 250 && r(blue) <= 5 && g(blue) <= 5)
    }

    @Test fun lumaMatchesTheRgbFormulaOnGrey() {
        for (y in 16..235) {
            val p = Yuv.rgb(y, 128, 128)
            assertEquals(Yuv.luma(y), (r(p) * 77 + g(p) * 150 + b(p) * 29) shr 8)
        }
    }
}
