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

    @Test fun severalOfAGroup() {
        // 37 lands in 99: exactly 4 in the opening 7 is C(37,4)·C(62,3)/C(99,7) ≈ 0.1678.
        assertEquals(0.1678, DrawOddsMath.exactly(99, 37, 7, 4), 0.001)
        val hand = DrawOddsMath.openingHand(99, 37)
        assertEquals(8, hand.size)
        assertEquals(1.0, hand.sum(), 1e-9)
        assertEquals(hand.drop(4).sum(), DrawOddsMath.atLeast(99, 37, 7, 4), 1e-9)
        assertEquals(DrawOddsMath.atLeastOne(60, 4, 7), DrawOddsMath.atLeast(60, 4, 7, 1), 1e-12)
        assertEquals(0.0, DrawOddsMath.atLeast(60, 2, 7, 3), 0.0)
        val two = DrawOddsMath.byTurn(99, 37, onPlay = true, atLeast = 2)
        val one = DrawOddsMath.byTurn(99, 37, onPlay = true)
        assertTrue(two.indices.all { two[it] <= one[it] })
    }
}
