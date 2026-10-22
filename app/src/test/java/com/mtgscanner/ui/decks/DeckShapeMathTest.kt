package com.mtgscanner.ui.decks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckShapeMathTest {
    @Test fun curveSkipsLandsAndBucketsSevenPlus() {
        val s = DeckShapeMath.of(
            listOf(
                ShapeCard(4, 1.0, "Instant", "{R}"),
                ShapeCard(20, 0.0, "Basic Land — Mountain", ""),
                ShapeCard(1, 0.0, "Artifact", "{0}"),
                ShapeCard(2, 7.0, "Creature — Eldrazi", "{7}"),
                ShapeCard(1, 10.0, "Creature — Eldrazi", "{10}"),
                ShapeCard(3, 2.5, "Creature", "{2}{G}"),
            ),
        )
        assertEquals(listOf(1, 4, 3, 0, 0, 0, 0, 3), s.curve)
        assertEquals(11, s.spells)
    }

    @Test fun mdfcSpellLandCountsAsSpell() {
        assertFalse(DeckShapeMath.isLand("Sorcery // Land"))
        assertTrue(DeckShapeMath.isLand("Land // Land"))
        assertTrue(DeckShapeMath.isLand("Legendary Land"))
    }

    @Test fun pipsCountHybridBothAndPhyrexian() {
        assertEquals(mapOf('W' to 1, 'U' to 1), DeckShapeMath.pips("{W/U}"))
        assertEquals(mapOf('G' to 1), DeckShapeMath.pips("{G/P}"))
        assertEquals(mapOf('W' to 1), DeckShapeMath.pips("{2/W}"))
        assertEquals(mapOf('U' to 2), DeckShapeMath.pips("{X}{U}{U}"))
        assertEquals(emptyMap<Char, Int>(), DeckShapeMath.pips("{3}{C}"))
        assertEquals(mapOf('U' to 1), DeckShapeMath.pips("{1}{U} // {3}{U}{U}"))
    }

    @Test fun colorSplitIsWubrgOrderAndTimesCopies() {
        val s = DeckShapeMath.of(
            listOf(
                ShapeCard(4, 1.0, "Instant", "{R}"),
                ShapeCard(2, 2.0, "Creature", "{W}{W}"),
                ShapeCard(1, 3.0, "Land", "{G}"),
            ),
        )
        assertEquals(listOf('W' to 4, 'R' to 4), s.pips)
        assertEquals(8, s.pipTotal)
    }

    @Test fun percentsAddToHundred() {
        assertEquals(listOf(34, 33, 33), DeckShapeMath.percents(listOf(1, 1, 1)))
        assertEquals(listOf(50, 50), DeckShapeMath.percents(listOf(4, 4)))
        assertEquals(emptyList<Int>(), DeckShapeMath.percents(listOf(0, 0)))
    }
}
