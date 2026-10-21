package com.mtgscanner.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test fun hugeQuantityDoesNotCrash() {
        val lines = DecklistParser.parse("99999999999 Island")
        assertEquals(9999, lines[0].quantity)
    }

    @Test fun skipsArenaPreamble() {
        val deck = DecklistParser.parse("About\nName Mono Red\n\nDeck\n20 Mountain\n\nSideboard\n2 Duress")
        assertEquals(listOf("Mountain", "Duress"), deck.map { it.name })
        assertEquals("Sideboard", deck[1].section)
    }

    @Test fun stripsArchidektTags() {
        val deck = DecklistParser.parse(
            "1x Sol Ring (cmr) 263 [Ramp]\n1 Swords to Plowshares [Removal,Exile] ^Have,#37d67a^\n1 Arcane Signet *F* [Ramp]\n1 Counterspell [MH2]",
        )
        val ring = deck.first { it.name == "Sol Ring" }
        assertEquals("cmr", ring.setCode)
        assertEquals("263", ring.collectorNumber)
        val swords = deck.first { it.name == "Swords to Plowshares" }
        assertEquals(null, swords.setCode)
        assertEquals(null, deck.first { it.name == "Arcane Signet" }.setCode)
        assertEquals("mh2", deck.first { it.name == "Counterspell" }.setCode)
    }

    @Test fun mtgoBlankLineSideboard() {
        val deck = DecklistParser.parse("24 Mountain\n36 Lightning Bolt\n\n4 Duress\n11 Pyroblast\n")
        assertEquals("Main", deck.first { it.name == "Mountain" }.section)
        assertEquals("Sideboard", deck.first { it.name == "Duress" }.section)
        assertEquals("Sideboard", deck.first { it.name == "Pyroblast" }.section)
    }

    @Test fun blankLinesAloneDoNotMakeSideboard() {
        // Too small for a 60-card deck: blank lines are just grouping.
        val small = DecklistParser.parse("20 Mountain\n\n4 Duress")
        assertEquals(listOf("Main", "Main"), small.map { it.section })
        // A header anywhere means the list says where things go.
        val headed = DecklistParser.parse("Deck\n60 Mountain\n\n4 Duress")
        assertEquals("Main", headed.first { it.name == "Duress" }.section)
        // Commander-sized last block is not a sideboard.
        val big = DecklistParser.parse("60 Mountain\n\n40 Island")
        assertEquals("Main", big.first { it.name == "Island" }.section)
    }

    @Test fun samePrintingsStaySeparate() {
        val deck = DecklistParser.parse("5 Island (ONE) 270\n5 Island (DMU) 263")
        assertEquals(2, deck.size)
    }

    @Test fun differentNumbersFromOneSetStayApart() {
        val deck = DecklistParser.parse("1 Forest (ZEN) 246\n1 Forest (ZEN) 247\n1 Forest (ZEN) 246")
        assertEquals(2, deck.size)
        assertEquals(2, deck.first { it.collectorNumber == "246" }.quantity)
        assertEquals(1, deck.first { it.collectorNumber == "247" }.quantity)
    }

    @Test fun groupHeadersAreNotCards() {
        val deck = DecklistParser.parse(
            "Commander (1)\n1 Atraxa, Praetors' Voice\n\nCreatures (24)\n1 Llanowar Elves\nLands\n1 Forest\nRamp (2)\n1 Sol Ring\n",
        )
        assertEquals(listOf("Atraxa, Praetors' Voice", "Llanowar Elves", "Forest", "Sol Ring"), deck.map { it.name })
        assertEquals("Commander", deck[0].section)
        // A group header after the commander goes back to the main deck.
        assertEquals("Main", deck[1].section)
        assertEquals("Main", deck[3].section)
    }

    @Test fun keepsFoilMarker() {
        val deck = DecklistParser.parse("1 Sol Ring (CMR) 263 *F*\n1 Sol Ring (CMR) 263\n2 Arcane Signet *E*")
        assertEquals(3, deck.size)
        assertTrue(deck.first { it.name == "Sol Ring" && it.foil }.collectorNumber == "263")
        assertTrue(deck.single { it.name == "Arcane Signet" }.foil)
        assertFalse(deck.first { it.name == "Sol Ring" && !it.foil }.foil)
    }
}
