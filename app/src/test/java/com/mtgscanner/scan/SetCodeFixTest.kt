package com.mtgscanner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SetCodeFixTest {
    private val known = mapOf("otj" to 286, "mid" to 277, "m21" to 397, "mkm" to 286, "neo" to 302, "lci" to 291, "big" to 30)

    @Test
    fun keepsARealCode() {
        assertEquals("OTJ", SetCodeFix.fix("otj", known))
    }

    @Test
    fun fixesLookAlikes() {
        assertEquals("OTJ", SetCodeFix.fix("0TJ", known))
        assertEquals("MID", SetCodeFix.fix("M1D", known))
        assertEquals("M21", SetCodeFix.fix("MZ1", known))
        assertEquals("NEO", SetCodeFix.fix("NE0", known))
        // Two swaps.
        assertEquals("LCI", SetCodeFix.fix("1C1", known))
    }

    @Test
    fun leavesUnknownReads() {
        assertNull(SetCodeFix.fix("XYZ", known))
        // Nothing to correct against yet.
        assertEquals("0TJ", SetCodeFix.fix("0TJ", emptyMap()))
    }

    @Test
    fun prefersASetBigEnoughForTheNumber() {
        // "8IG" could be BIG (30 cards); with number 120 it's no fit, but it's the only candidate, so it stays.
        assertEquals("BIG", SetCodeFix.fix("8IG", known, "120"))
        val two = mapOf("abc" to 50, "a8c" to 300)
        // "ABC" is real: kept as read.
        assertEquals("ABC", SetCodeFix.fix("ABC", two, "120"))
        val both = mapOf("bbc" to 50, "88c" to 300)
        // "B8C" reaches both; only one holds a number 120.
        assertEquals("88C", SetCodeFix.fix("B8C", both, "120"))
    }

    @Test
    fun ambiguousIsLeftAlone() {
        val tie = mapOf("bbc" to 300, "88c" to 300)
        assertNull(SetCodeFix.fix("B8C", tie, "120"))
    }
}
