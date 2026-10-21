package com.mtgscanner.ui.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionFanMathTest {
    private val fan = FanLayout(cardWidth = 100f, step = 16f, dx = 5f, front = 40f, max = 8)

    @Test fun emptyFanHasNoSize() {
        assertEquals(0f, fan.width(0), 0f)
        assertEquals(0f, fan.height(0), 0f)
    }

    @Test fun oneCardShowsOnlyItsFront() {
        assertEquals(100f, fan.width(1), 0f)
        assertEquals(40f, fan.height(1), 0f)
    }

    @Test fun sizeStopsGrowingAtMax() {
        assertEquals(100f + 7 * 5f, fan.width(8), 0f)
        assertEquals(7 * 16f + 40f, fan.height(8), 0f)
        assertEquals(fan.height(8), fan.height(30), 0f)
        assertEquals(8, fan.shown(30))
    }

    @Test fun newerCardsSitLowerAndRight() {
        assertTrue(fan.slotY(3f) > fan.slotY(2f))
        assertTrue(fan.slotX(3f) > fan.slotX(2f))
    }

    @Test fun keysGoBackToFrontOldestFirst() {
        assertEquals(listOf(3L, 4L, 5L), fanKeys(listOf(5L, 4L, 3L, 2L, 1L), 3))
        assertEquals(emptyList<Long>(), fanKeys(emptyList(), 8))
    }

    @Test fun onlyRealAddsFly() {
        assertTrue(isNewAdd(null, 1L to 1))
        assertTrue(isNewAdd(1L to 1, 2L to 1))
        assertTrue(isNewAdd(1L to 1, 1L to 2))
        assertFalse(isNewAdd(1L to 2, 1L to 2))
        assertFalse(isNewAdd(1L to 1, null))
    }
}
