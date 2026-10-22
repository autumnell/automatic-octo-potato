package com.mtgscanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CopiesLineTest {
    private val spots = mapOf(
        1L to CopiesLine.Spot("Rares"),
        2L to CopiesLine.Spot("Atraxa", deck = true),
        3L to CopiesLine.Spot("Binder"),
    )

    @Test fun namesBoxesAndDecksMostCopiesFirst() {
        assertEquals("3 copies: Rares ×2, Atraxa deck ×1", CopiesLine.text(listOf(2L to 1, 1L to 1, 1L to 1), spots))
    }

    @Test fun oneRowSaysNothing() {
        assertNull(CopiesLine.text(listOf(1L to 4), spots))
        assertNull(CopiesLine.text(emptyList(), spots))
        assertNull(CopiesLine.text(listOf(1L to 4, 3L to 0), spots))
    }

    @Test fun unknownPlaceIsUnsorted() {
        assertEquals("2 copies: Binder ×1, Unsorted ×1", CopiesLine.text(listOf(3L to 1, 99L to 1), spots))
    }
}
