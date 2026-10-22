package com.mtgscanner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenNamesTest {
    private val matcher = CardNameMatcher(
        listOf("Ornithopter", "Soldier of Fortune", "Treasure Cruise", "Clue Hunter"),
        extras = listOf("Ornithopter", "Treasure", "Soldier", "Clue", "Elspeth, Knight-Errant Emblem"),
    )

    @Test fun tokenNamesAreFound() {
        val m = matcher.best("Treasure")
        assertEquals("Treasure", m?.name)
        assertTrue(m!!.extra)
        assertTrue(m.score >= 0.85)
        assertTrue(matcher.isExtra("Treasure"))
    }

    @Test fun cardNameWinsWhenShared() {
        val m = matcher.best("Ornithopter")
        assertEquals("Ornithopter", m?.name)
        assertFalse(m!!.extra)
        assertFalse(matcher.isExtra("Ornithopter"))
    }

    @Test fun cardNameWinsATie() {
        // One letter off a card and a token: the card.
        val tie = CardNameMatcher(listOf("Gobline"), extras = listOf("Goblins"))
        val m = tie.best("Goblinx")
        assertEquals("Gobline", m?.name)
        assertFalse(m!!.extra)
        // Order of the lists doesn't matter for the tie.
        val tie2 = CardNameMatcher(listOf("Goblins"), extras = listOf("Gobline"))
        assertEquals("Goblins", tie2.best("Goblinx")?.name)
        // The token is met first (shorter), the card later still wins the tie.
        val tie3 = CardNameMatcher(listOf("Goblinsx"), extras = listOf("Goblin"))
        assertEquals("Goblinsx", tie3.best("Goblinx")?.name)
    }

    @Test fun closerTokenStillWins() {
        val m = CardNameMatcher(listOf("Treasure Cruise"), extras = listOf("Treasure")).best("Treasure")
        assertEquals("Treasure", m?.name)
    }

    @Test fun exactTokenScoresBelowExactCard() {
        val t = matcher.best("Soldier")!!
        assertTrue(t.extra)
        assertTrue(t.score < 1.0)
    }
}
