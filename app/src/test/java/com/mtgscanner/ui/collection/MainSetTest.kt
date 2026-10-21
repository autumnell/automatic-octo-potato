package com.mtgscanner.ui.collection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainSetTest {
    @Test fun printedSizeDecides() {
        assertTrue(MainSet.isMain("1", emptyList(), 281))
        assertTrue(MainSet.isMain("281", emptyList(), 281))
        assertFalse(MainSet.isMain("282", emptyList(), 281))
        assertFalse(MainSet.isMain("350", listOf("showcase"), 281))
    }

    @Test fun lettersAndSymbolsAreVariants() {
        assertFalse(MainSet.isMain("12p", emptyList(), 281))
        assertFalse(MainSet.isMain("45★", emptyList(), null))
        assertFalse(MainSet.isMain("0", emptyList(), null))
    }

    @Test fun treatmentsWithoutPrintedSize() {
        assertTrue(MainSet.isMain("10", emptyList(), null))
        assertTrue(MainSet.isMain("10", listOf("fullart"), null))
        assertFalse(MainSet.isMain("10", listOf("borderless"), null))
        assertFalse(MainSet.isMain("10", MainSet.treatmentsOf("extendedart, etched"), null))
        assertFalse(MainSet.isMain("10", listOf("promo"), null))
    }

    @Test fun totals() {
        assertEquals(281, MainSet.total(420, 281, includeVariants = false))
        assertEquals(420, MainSet.total(420, 281, includeVariants = true))
        assertEquals(300, MainSet.total(300, null, includeVariants = false))
    }
}
