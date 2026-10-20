package com.mtgscanner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardNameMatcherTest {
    private val matcher = CardNameMatcher(
        listOf("Lightning Bolt", "Lightning Helix", "Llanowar Elves", "Fire // Ice", "Sol Ring", "Counterspell", "Brainstorm"),
    )

    @Test fun exactNameMatches() {
        assertEquals("Sol Ring", matcher.best("Sol Ring")?.name)
    }

    @Test fun ocrTyposAreCorrected() {
        val m = matcher.best("Lightnirg Bo1t")
        assertEquals("Lightning Bolt", m?.name)
        assertTrue(m!!.score >= 0.8)
    }

    @Test fun eitherFaceOfASplitCardMatches() {
        assertEquals("Fire // Ice", matcher.best("Ice")?.name ?: matcher.best("Fire")?.name)
        assertEquals("Fire // Ice", matcher.best("Fire")?.name)
    }

    @Test fun junkDoesNotMatch() {
        assertNull(matcher.best("Creature — Elf Druid"))
        assertNull(matcher.best("xq"))
    }

    @Test fun boundedLevenshteinStopsEarly() {
        assertEquals(1, CardNameMatcher.boundedLevenshtein("kitten", "sitten", 5))
        assertEquals(2, CardNameMatcher.boundedLevenshtein("abcdef", "uvwxyz", 2))
    }
}
