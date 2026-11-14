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

    @Test fun aFooterReadOnSomeFramesIsStillOneCard() {
        val k = VideoKeys()
        val c = VideoCount<String>()
        c.frame(listOf(k.of("bolt", null) to "a"))
        c.frame(listOf(k.of("bolt", "M10#146") to "b"))
        c.frame(listOf(k.of(null, "M10#146") to "c"))
        c.frame(listOf(k.of("bolt", null) to "d"))
        assertEquals(listOf("a"), c.result())
    }

    @Test fun anotherPrintingOfTheSameNameIsAnotherCard() {
        val k = VideoKeys()
        val a = k.of("forest", "DMU#277")
        val b = k.of("forest", "ONE#271")
        assertTrue(a != b)
        // A frame with only the name goes with the newest printing.
        assertEquals(b, k.of("forest", null))
        assertEquals(a, k.of(null, "DMU#277"))
        assertEquals(null, k.of(null, null))
    }
}
