package com.mtgscanner.ui.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoTorchTest {
    private fun frames(t: AutoTorch, luma: Int, n: Int, torch: Boolean, now: Long = 0): Boolean? {
        var last: Boolean? = null
        repeat(n) { t.onFrame(luma, torch, enabled = true, paused = false, now = now)?.let { last = it } }
        return last
    }

    @Test fun comesOnInTheDarkAndOffWhenBright() {
        val t = AutoTorch()
        assertNull(frames(t, 20, AutoTorch.DARK_FRAMES - 1, torch = false))
        assertEquals(true, frames(t, 20, 1, torch = false))
        assertNull(frames(t, 250, AutoTorch.BRIGHT_FRAMES - 1, torch = true))
        assertEquals(false, frames(t, 250, 1, torch = true))
        // Waits before coming back on.
        assertNull(frames(t, 20, 20, torch = false, now = 5_000))
        assertEquals(true, frames(t, 20, 1, torch = false, now = 20_000))
    }

    @Test fun leftAloneOnceTurnedByHand() {
        val t = AutoTorch()
        t.turnedByHand()
        assertNull(frames(t, 20, 30, torch = false))
    }

    @Test fun glareHintNeedsTwoFrames() {
        val g = GlareHint()
        assertNull(g.onFrame(true))
        assertEquals(true, g.onFrame(true))
        assertNull(g.onFrame(false))
        assertEquals(false, g.onFrame(false))
    }
}
