package com.mtgscanner.ui

import com.mtgscanner.ui.components.ArtTint
import com.mtgscanner.ui.home.ColorSplit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdeasFiveTest {
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun artTintFindsTheMainHue() {
        // Mostly red art with a little blue.
        val px = IntArray(100) { if (it < 80) rgb(200, 40, 30) else rgb(30, 60, 200) }
        val picked = ArtTint.pick(px)
        assertNotNull(picked)
        val (hue, sat) = picked!!
        assertTrue("hue $hue", hue < 20f || hue > 340f)
        assertTrue(sat > 0.5f)
    }

    @Test
    fun artTintSkipsGreyAndDarkArt() {
        assertNull(ArtTint.pick(IntArray(100) { rgb(120, 120, 120) }))
        assertNull(ArtTint.pick(IntArray(100) { rgb(10, 5, 20) }))
    }

    @Test
    fun colorSplitBuckets() {
        assertEquals(0, ColorSplit.indexOf("W"))
        assertEquals(4, ColorSplit.indexOf("g"))
        assertEquals(5, ColorSplit.indexOf("WU"))
        assertEquals(6, ColorSplit.indexOf(""))
        assertEquals("Multicolor", ColorSplit.label("M"))
    }
}
