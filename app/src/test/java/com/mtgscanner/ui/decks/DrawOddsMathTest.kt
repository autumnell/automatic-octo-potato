package com.mtgscanner.ui.decks

import com.mtgscanner.util.DeckLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawOddsMathTest {
    @Test fun oneOfFourInSixty() {
        // 1 - C(56,7)/C(60,7) ≈ 0.3995.
        assertEquals(0.3995, DrawOddsMath.atLeastOne(60, 4, 7), 0.001)
        assertEquals(0.0, DrawOddsMath.atLeastOne(60, 0, 7), 0.0)
        assertEquals(1.0, DrawOddsMath.atLeastOne(10, 5, 6), 0.0)
    }

    @Test fun onThePlayDrawsOneFewer() {
        assertEquals(7, DrawOddsMath.seenBy(1, onPlay = true))
        assertEquals(8, DrawOddsMath.seenBy(1, onPlay = false))
        val play = DrawOddsMath.byTurn(99, 10, onPlay = true)
        val draw = DrawOddsMath.byTurn(99, 10, onPlay = false)
        assertEquals(DrawOddsMath.TURNS, play.size)
        assertTrue(draw[0] > play[0])
        assertTrue(play.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test fun libraryIsTheMainDeck() {
        val lines = listOf(DeckLine(1, "Atraxa", section = "Commander"), DeckLine(30, "Forest"), DeckLine(2, "Sol Ring"), DeckLine(1, "Sol Ring", "cmr", "263"), DeckLine(2, "Duress", section = "Sideboard"))
        val lib = DrawOddsMath.library(lines) { if (it == "Forest") "Basic Land — Forest" to "" else null }
        assertEquals(33, lib.sumOf { it.copies })
        assertEquals(30, lib.filter { OddsGroup.LANDS.has(it) }.sumOf { it.copies })
    }

    @Test fun groups() {
        assertTrue(OddsGroup.RAMP.has(OddsCard("Sol Ring", 1, "Artifact", "{T}: Add {C}{C}.")))
        assertTrue(OddsGroup.RAMP.has(OddsCard("Cultivate", 1, "Sorcery", "Search your library for up to two basic land cards")))
        assertTrue(OddsGroup.DRAW.has(OddsCard("Divination", 1, "Sorcery", "Draw two cards.")))
        assertTrue(OddsGroup.REMOVAL.has(OddsCard("Doom Blade", 1, "Instant", "Destroy target nonblack creature.")))
        assertTrue(!OddsGroup.RAMP.has(OddsCard("Forest", 1, "Basic Land — Forest", "({T}: Add {G}.)")))
    }
}
