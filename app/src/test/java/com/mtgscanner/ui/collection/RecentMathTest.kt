package com.mtgscanner.ui.collection

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentMathTest {
    @Test fun pushPutsNewestFirstWithoutRepeats() {
        assertEquals(listOf(3L, 1L, 2L), RecentMath.push(listOf(1L, 2L, 3L), 3L))
        assertEquals(listOf(4L, 1L, 2L), RecentMath.push(listOf(1L, 2L), 4L))
    }

    @Test fun pushKeepsTen() {
        val full = (1L..10L).toList()
        val next = RecentMath.push(full, 11L)
        assertEquals(10, next.size)
        assertEquals(11L, next.first())
        assertEquals(9L, next.last())
    }

    @Test fun pushIgnoresBadIds() {
        assertEquals(listOf(1L), RecentMath.push(listOf(1L), 0L))
    }

    @Test fun encodeDecodeRoundTripsAndSkipsJunk() {
        assertEquals(listOf(5L, 2L), RecentMath.decode(RecentMath.encode(listOf(5L, 2L))))
        assertEquals(listOf(5L, 2L), RecentMath.decode("5,x,,2,5,-1"))
        assertEquals(emptyList<Long>(), RecentMath.decode(null))
    }

    @Test fun inOrderKeepsIdOrderAndDropsGoneCards() {
        val found = listOf(2L to "b", 1L to "a")
        assertEquals(listOf("a", "b"), RecentMath.inOrder(listOf(1L, 9L, 2L), found) { it.first }.map { it.second })
    }
}
