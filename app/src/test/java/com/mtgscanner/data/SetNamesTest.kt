package com.mtgscanner.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SetNamesTest {
    private val index = SetNames.index(
        listOf("Magic 2010" to "M10", "Time Spiral Timeshifted" to "TSB", "Core Set 2019" to "M19", "Commander Legends" to "CMR"),
    )

    @Test fun matchesFullNames() {
        assertEquals("m10", SetNames.codeFor("Magic 2010", index))
        assertEquals("tsb", SetNames.codeFor("Time Spiral \"Timeshifted\"", index))
        assertEquals("m19", SetNames.codeFor("Core Set 2019", index))
        assertEquals("cmr", SetNames.codeFor("commander legends", index))
        assertEquals(null, SetNames.codeFor("Nowhere", index))
    }
}
