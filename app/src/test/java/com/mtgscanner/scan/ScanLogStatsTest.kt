package com.mtgscanner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanLogStatsTest {
    @Test
    fun summarizes() {
        val list = listOf(
            ScanLogEntry(outcome = "matched", ms = 300, seenMs = 700),
            ScanLogEntry(outcome = "matched", ms = 100, seenMs = 500),
            ScanLogEntry(outcome = "matched", ms = 200, seenMs = 900),
            ScanLogEntry(outcome = "no match", ms = 400),
        )
        val s = ScanLogStats.of(list)!!
        assertEquals(4, s.lookups)
        assertEquals(0.75, s.matchedShare, 1e-9)
        assertEquals(700L, s.medianSeenMs)
        assertEquals(300L, s.medianLookupMs)
        assertEquals("75% matched · 0.7 s per card (lookup 0.3 s)", s.text)
    }

    @Test
    fun emptyHasNone() {
        assertNull(ScanLogStats.of(emptyList()))
    }
}
