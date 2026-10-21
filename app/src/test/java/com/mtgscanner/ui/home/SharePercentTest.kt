package com.mtgscanner.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class SharePercentTest {
    @Test fun roundsToWholePercent() {
        assertEquals("22%", sharePercent(22, 100))
        assertEquals("33%", sharePercent(1, 3))
        assertEquals("67%", sharePercent(2, 3))
    }

    @Test fun tinySharesShowLessThanOne() {
        assertEquals("<1%", sharePercent(1, 500))
    }

    @Test fun emptyIsZero() {
        assertEquals("0%", sharePercent(0, 100))
        assertEquals("0%", sharePercent(0, 0))
    }
}
