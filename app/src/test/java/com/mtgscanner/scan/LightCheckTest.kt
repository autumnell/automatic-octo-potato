package com.mtgscanner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LightCheckTest {
    @Test fun measuresBrightness() {
        val dark = LightCheck.measure(100, 100, { _, _ -> 0x101010 })
        assertTrue(dark.luma < LightCheck.DARK)
        val white = LightCheck.measure(100, 100, { _, _ -> 0xFFFFFF })
        assertEquals(255, white.luma)
        assertEquals(1f, white.glare, 0.001f)
    }

    @Test fun foilNeedsChangingSheen() {
        val steady = List(8) { FrameLight(120, 0.05f, 0.01f) }
        assertFalse(LightCheck.looksFoil(steady))
        val shifting = List(8) { i -> if (i % 2 == 0) FrameLight(130, 0.02f, 0.01f) else FrameLight(140, 0.08f, 0.06f) }
        assertTrue(LightCheck.looksFoil(shifting))
        assertFalse(LightCheck.looksFoil(shifting.take(3)))
    }
}
