package com.mtgscanner.ui.decks

import com.mtgscanner.util.DecklistParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextIdeas3DeckTest {
    @Test fun twoDecksShortOnSharedCards() {
        val a = DecklistParser.parse("1 Sol Ring\n1 Island\n1 Opt\n1 Lonely")
        val b = DecklistParser.parse("1 Sol Ring\n1 Island\n2 Opt")
        val owned = mapOf(com.mtgscanner.util.CardNames.key("Sol Ring") to 1, com.mtgscanner.util.CardNames.key("Opt") to 3)
        val rows = SharedCards.both(a, b, owned)
        assertEquals(listOf("Sol Ring", "Opt"), rows.map { it.name })
        assertEquals(1, rows[0].short)
        assertEquals(0, rows[1].short)
    }

    @Test fun savedShapeComesBack() {
        val saved = DeckPageCache.Saved("s", curve = listOf(0, 1, 2, 3, 0, 0, 0, 1), pips = "W3 G2")
        val shape = saved.shape()!!
        assertEquals(7, shape.spells)
        assertEquals(listOf('W' to 3, 'G' to 2), shape.pips)
        assertNull(DeckPageCache.Saved("s", curve = listOf(1)).shape())
    }
}
