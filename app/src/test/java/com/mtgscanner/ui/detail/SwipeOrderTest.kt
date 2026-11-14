package com.mtgscanner.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwipeOrderTest {
    @Test fun theListIsHandedOverOnce() {
        SwipeOrder.set(listOf(3L, 5L, 9L))
        assertEquals(listOf(3L, 5L, 9L), SwipeOrder.take(5))
        assertNull(SwipeOrder.take(5))
    }

    /** A card opened from somewhere else must not swipe through an older list, then or later. */
    @Test fun aCardFromElsewhereGetsNoListAndClearsIt() {
        SwipeOrder.set(listOf(1L, 2L))
        assertNull(SwipeOrder.take(7))
        assertNull(SwipeOrder.take(1))
    }
}
