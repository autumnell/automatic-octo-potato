package com.mtgscanner.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RollingMathTest {
    @Test fun noChangeNoAnimation() {
        assertEquals(0, RollingMath.durationMillis(12.5, 12.5))
    }

    @Test fun biggerJumpsTakeLongerButStayQuick() {
        val small = RollingMath.durationMillis(10.0, 11.0)
        val big = RollingMath.durationMillis(0.0, 5_000.0)
        val huge = RollingMath.durationMillis(0.0, 5_000_000.0)
        assertTrue(small in 450..700)
        assertTrue(big > small)
        assertEquals(1100, huge)
        assertEquals(RollingMath.durationMillis(0.0, 300.0), RollingMath.durationMillis(300.0, 0.0))
    }
}
