package com.mtgscanner.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtAutoPickTest {
    @Test fun biggestSetsNotYetIndexed() {
        val owned = listOf("mkm", "otj", "blb", "dsk", "fdn", "woe", "lci")
        assertEquals(listOf("mkm", "blb", "dsk", "fdn", "woe"), ArtAutoPick.pick(owned, setOf("otj")))
    }

    @Test fun oddCodesSkipped() {
        assertEquals(listOf("mkm"), ArtAutoPick.pick(listOf("", "p-x", "MKM"), emptySet(), n = 3))
    }
}
