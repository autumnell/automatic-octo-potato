package com.mtgscanner.ui.decks.playtest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaytestFormatsTest {
    @Test fun ownDecksMatchExactly() {
        assertTrue(PlaytestFormats.sameFormat("modern", "modern"))
        assertFalse(PlaytestFormats.sameFormat("modern", "standard"))
        assertTrue(PlaytestFormats.sameFormat("", ""))
        assertFalse(PlaytestFormats.sameFormat("", "commander"))
    }

    @Test fun preconsByFormat() {
        assertTrue(PlaytestFormats.preconFits("commander", isCommanderPrecon = true, commanderFormat = true))
        assertFalse(PlaytestFormats.preconFits("commander", isCommanderPrecon = false, commanderFormat = true))
        assertTrue(PlaytestFormats.preconFits("standard", isCommanderPrecon = false, commanderFormat = false))
        assertFalse(PlaytestFormats.preconFits("modern", isCommanderPrecon = true, commanderFormat = false))
        assertFalse(PlaytestFormats.preconFits("brawl", isCommanderPrecon = true, commanderFormat = true))
        assertTrue(PlaytestFormats.preconFits("", isCommanderPrecon = true, commanderFormat = false))
    }

    @Test fun lifeByFormat() {
        val c = PlayCard("Leader", typeLine = "Legendary Creature")
        assertEquals(40, Playtest.startingLife(PlayDeck("a", emptyList(), listOf(c), "commander")))
        assertEquals(30, Playtest.startingLife(PlayDeck("a", emptyList(), listOf(c), "paupercommander")))
        assertEquals(25, Playtest.startingLife(PlayDeck("a", emptyList(), listOf(c), "brawl")))
        assertEquals(20, Playtest.startingLife(PlayDeck("a", emptyList(), listOf(c), "oathbreaker")))
        assertEquals(20, Playtest.startingLife(PlayDeck("a", emptyList(), emptyList(), "modern")))
        assertEquals(40, Playtest.startingLife(PlayDeck("a", emptyList(), listOf(c), "")))
        assertEquals(20, Playtest.startingLife(PlayDeck("a", emptyList(), emptyList(), "")))
    }
}
