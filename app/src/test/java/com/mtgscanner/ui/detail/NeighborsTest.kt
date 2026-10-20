package com.mtgscanner.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class NeighborsTest {
    @Test fun middleFirstLastAndMissing() {
        val order = listOf(5L, 2L, 9L)
        assertEquals(Neighbors(5L, 9L, 2, 3), neighborsIn(2L, order))
        assertEquals(Neighbors(null, 2L, 1, 3), neighborsIn(5L, order))
        assertEquals(Neighbors(2L, null, 3, 3), neighborsIn(9L, order))
        assertEquals(Neighbors(), neighborsIn(7L, order))
    }
}
