package com.mtgscanner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintPoolTest {
    @Test fun poolsWhatTwoFramesAgreeOn() {
        val p = PrintPool()
        p.add(listOf(floatArrayOf(1f, 0f, 0f)))
        assertTrue(p.pooled().isEmpty())
        p.add(listOf(floatArrayOf(0.9f, 0.1f, 0f), floatArrayOf(0f, 0f, 1f)))
        assertEquals(1, p.pooled().size)
        p.clear()
        assertTrue(p.pooled().isEmpty())
    }
}
