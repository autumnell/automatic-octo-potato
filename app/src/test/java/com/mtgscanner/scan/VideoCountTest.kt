package com.mtgscanner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoCountTest {
    @Test fun aCardStayingInViewCountsOnce() {
        val c = VideoCount<String>()
        c.frame(listOf("bolt" to "b1"))
        c.frame(listOf("bolt" to "b2"))
        c.frame(emptyList(), skipped = true)
        c.frame(listOf("bolt" to "b3", "elf" to "e1"))
        assertEquals(listOf("b1", "e1"), c.result())
    }

    @Test fun twoCopiesTogetherCountTwiceAndAReturnCountsAgain() {
        val c = VideoCount<String>(gap = 2)
        c.frame(listOf("bolt" to "a", "bolt" to "b"))
        c.frame(listOf("bolt" to "c"))
        repeat(4) { c.frame(emptyList()) }
        c.frame(listOf("bolt" to "d"))
        assertEquals(listOf("a", "b", "d"), c.result())
    }

    @Test fun nothingReadIsLeftOut() {
        val c = VideoCount<String>()
        c.frame(listOf(null to "x"))
        assertEquals(emptyList<String>(), c.result())
    }

    @Test fun blurHasLessEdgeThanSharp() {
        val w = 32
        val sharp = IntArray(w * w) { i -> if ((i % w) / 4 % 2 == 0) 0 else 255 }
        val blur = IntArray(w * w) { i -> ((i % w) * 8).coerceAtMost(255) }
        assertTrue(Sharpness.laplacianVariance(sharp, w, w) > 10 * Sharpness.laplacianVariance(blur, w, w))
    }
}
