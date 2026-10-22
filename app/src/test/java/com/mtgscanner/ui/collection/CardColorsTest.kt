package com.mtgscanner.ui.collection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardColorsTest {
    private val cards = listOf("", "W", "U", "WU", "UW", "WUB", "R", "BG")

    private fun pick(sel: Set<String>, mode: ColorMatch) = cards.filter { CardColors.matches(sel, mode, it) }

    @Test fun nothingPickedMatchesAll() {
        assertEquals(cards, pick(emptySet(), ColorMatch.INCLUDING))
    }

    @Test fun includingNeedsEveryColor() {
        assertEquals(listOf("W", "WU", "UW", "WUB"), pick(setOf("W"), ColorMatch.INCLUDING))
        // Two colors: both, not either.
        assertEquals(listOf("WU", "UW", "WUB"), pick(setOf("W", "U"), ColorMatch.INCLUDING))
    }

    @Test fun exactlyMeansThoseColorsOnly() {
        assertEquals(listOf("WU", "UW"), pick(setOf("W", "U"), ColorMatch.EXACTLY))
        assertEquals(listOf("W"), pick(setOf("W"), ColorMatch.EXACTLY))
    }

    @Test fun atMostIsASubsetAndColorlessCounts() {
        assertEquals(listOf("", "W", "U", "WU", "UW"), pick(setOf("W", "U"), ColorMatch.AT_MOST))
    }

    @Test fun colorless() {
        for (m in ColorMatch.entries) assertEquals(listOf(""), pick(setOf("C"), m))
        // Colorless plus a color: that color's cards (by the mode) or colorless ones.
        assertEquals(listOf("", "R"), pick(setOf("C", "R"), ColorMatch.EXACTLY))
        assertEquals(listOf("", "W", "WU", "UW", "WUB"), pick(setOf("C", "W"), ColorMatch.INCLUDING))
    }

    @Test fun caseAndOrderDoNotMatter() {
        assertTrue(CardColors.matches(setOf("U", "W"), ColorMatch.EXACTLY, "wu"))
    }

    @Test fun oldKeysStillWork() {
        // Home's color-pair links: exactly that pair, whatever the mode.
        assertEquals(listOf("WU", "UW"), pick(setOf("WU"), ColorMatch.INCLUDING))
        assertEquals(listOf("WUB"), pick(setOf("3+"), ColorMatch.AT_MOST))
        // A saved "Multicolor" pick: two or more colors.
        assertEquals(listOf("WU", "UW", "WUB", "BG"), pick(setOf("M"), ColorMatch.INCLUDING))
        // With pips too, both have to match.
        assertEquals(listOf("WUB"), pick(setOf("M", "B", "W"), ColorMatch.INCLUDING))
        assertFalse(CardColors.matches(setOf("WU"), ColorMatch.INCLUDING, "WUB"))
    }

    @Test fun scryfallTerms() {
        assertEquals(null, CardColors.scryfallTerm(emptySet(), ColorMatch.INCLUDING, false))
        assertEquals("c>=wu", CardColors.scryfallTerm(setOf("U", "W"), ColorMatch.INCLUDING, false))
        assertEquals("c=wu", CardColors.scryfallTerm(setOf("W", "U"), ColorMatch.EXACTLY, false))
        assertEquals("id<=bg", CardColors.scryfallTerm(setOf("G", "B"), ColorMatch.AT_MOST, true))
        assertEquals("c=c", CardColors.scryfallTerm(setOf("C"), ColorMatch.INCLUDING, false))
        assertEquals("id=c", CardColors.scryfallTerm(setOf("C"), ColorMatch.AT_MOST, true))
        assertEquals("(c=r or c=c)", CardColors.scryfallTerm(setOf("C", "R"), ColorMatch.EXACTLY, false))
        assertEquals("c<=r", CardColors.scryfallTerm(setOf("C", "R"), ColorMatch.AT_MOST, false))
    }
}
