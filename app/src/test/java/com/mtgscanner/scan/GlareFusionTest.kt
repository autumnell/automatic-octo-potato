package com.mtgscanner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlareFusionTest {
    private val w = 120
    private val h = 30

    /** Dark letters (40) on a light strip (170), a pattern that's easy to line up. */
    private fun strip(dx: Int = 0, glareAt: Int? = null) = IntArray(w * h) { i ->
        val x = i % w - dx
        val y = i / w
        val ink = (x / 6 + y / 5) % 3 == 0 && y in 6..23
        when {
            glareAt != null && x in glareAt until glareAt + 20 -> 250
            ink -> 40
            else -> 170
        }
    }

    @Test fun findsTheShift() {
        assertEquals(3 to 0, GlareFusion.shift(strip(dx = 3), strip(), w, h))
        assertEquals(0 to 0, GlareFusion.shift(strip(), strip(), w, h))
    }

    @Test fun glareMovingBetweenFramesDropsOut() {
        val s = GlareStack()
        s.add(1L, strip(glareAt = 20), w, h)
        assertNull(s.fused())
        s.add(1L, strip(glareAt = 70), w, h)
        val fused = assertNotNullAnd(s.fused())
        val clean = strip()
        // Where either frame had glare, the fused strip is the clean one.
        for (x in 20 until 90) for (y in 0 until h) assertEquals(clean[y * w + x], fused[y * w + x])
    }

    @Test fun aNewCardStartsOver() {
        val s = GlareStack()
        s.add(1L, strip(), w, h)
        s.add(2L, strip(), w, h)
        assertEquals(1, s.size)
        s.add(2L, IntArray(60 * 30), 60, 30)
        assertEquals(1, s.size)
        assertEquals(60, s.width)
    }

    @Test fun fuseKeepsTheDarkerHalf() {
        val f = GlareFusion.fuse(listOf(intArrayOf(10), intArrayOf(250), intArrayOf(20), intArrayOf(240)))
        assertEquals(15, f[0])
        assertTrue(GlareFusion.fuse(listOf(intArrayOf(99)))[0] == 99)
    }

    private fun assertNotNullAnd(a: IntArray?): IntArray { assertNotNull(a); return a!! }
}
