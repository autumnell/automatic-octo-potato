package com.mtgscanner.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DecklistParserTest {
    @Test fun readsCommonFormats() {
        val deck = DecklistParser.parse(
            """
            Deck
            4 Lightning Bolt
            2x Counterspell
            1 Sol Ring (CMR) 263
            SB: 3 Duress

            Sideboard
            1 Pyroblast
            """.trimIndent(),
        )
        val bolt = deck.first { it.name == "Lightning Bolt" }
        assertEquals(4, bolt.quantity)
        assertEquals(2, deck.first { it.name == "Counterspell" }.quantity)
        val ring = deck.first { it.name == "Sol Ring" }
        assertEquals("cmr", ring.setCode?.lowercase())
        assertEquals("263", ring.collectorNumber)
        assertEquals("Sideboard", deck.first { it.name == "Duress" }.section)
        assertEquals("Sideboard", deck.first { it.name == "Pyroblast" }.section)
    }

    @Test fun mergesDuplicates() {
        val deck = DecklistParser.parse("2 Island\n3 Island")
        assertEquals(1, deck.size)
        assertEquals(5, deck[0].quantity)
    }
}
