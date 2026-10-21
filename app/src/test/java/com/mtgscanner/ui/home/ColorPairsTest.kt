package com.mtgscanner.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorPairsTest {
    @Test fun keyIgnoresOrder() {
        assertEquals("WU", ColorPairs.keyFor("UW"))
        assertEquals("GW", ColorPairs.keyFor("WG"))
        assertEquals("BG", ColorPairs.keyFor("GB"))
        assertNull(ColorPairs.keyFor("W"))
        assertNull(ColorPairs.keyFor("WUB"))
    }

    @Test fun countsPairsAndThreePlus() {
        val counts = ColorPairs.count(listOf("UW" to 2, "WU" to 1, "R" to 5, "" to 3, "WUB" to 4, "BG" to 1)).toMap()
        assertEquals(3, counts["WU"])
        assertEquals(1, counts["BG"])
        assertEquals(4, counts[ColorPairs.THREE_PLUS])
        assertEquals(0, counts["RW"])
        assertEquals(8, counts.values.sum())
    }

    @Test fun keepsOrder() {
        val keys = ColorPairs.count(emptyList()).map { it.first }
        assertEquals(ColorPairs.PAIRS.map { it.first } + ColorPairs.THREE_PLUS, keys)
    }

    @Test fun matchesForBrowse() {
        assertTrue(ColorPairs.matches("WU", "UW"))
        assertFalse(ColorPairs.matches("WU", "WUB"))
        assertTrue(ColorPairs.matches("3+", "WUBRG"))
        assertFalse(ColorPairs.matches("3+", "WU"))
    }
}
