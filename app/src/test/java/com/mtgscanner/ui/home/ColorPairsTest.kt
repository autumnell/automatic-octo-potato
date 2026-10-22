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

    @Test fun matchesForBrowse() {
        assertTrue(ColorPairs.matches("WU", "UW"))
        assertFalse(ColorPairs.matches("WU", "WUB"))
        assertTrue(ColorPairs.matches("3+", "WUBRG"))
        assertFalse(ColorPairs.matches("3+", "WU"))
    }
}
