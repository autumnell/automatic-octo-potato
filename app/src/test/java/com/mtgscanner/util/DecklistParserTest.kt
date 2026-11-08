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
        // A 100-card list in groups whose last group is small is still all deck.
        val grouped = DecklistParser.parse("1 Sol Ring\n91 Forest\n\n8 Llanowar Elves")
        assertEquals("Main", grouped.first { it.name == "Llanowar Elves" }.section)
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

    private fun one(line: String) = DecklistParser.parse(line).single()

    @Test fun setCodesFromRealExports() {
        // Moxfield / Arena
        one("1 Sol Ring (FDC) 123").let { assertEquals("Sol Ring", it.name); assertEquals("fdc", it.setCode); assertEquals("123", it.collectorNumber) }
        one("1 Sol Ring (fdc) 123 *F*").let { assertEquals("fdc", it.setCode); assertEquals("123", it.collectorNumber); assertTrue(it.foil) }
        one("1 Sol Ring (FDC)").let { assertEquals("fdc", it.setCode); assertEquals(null, it.collectorNumber) }
        one("1 Sol Ring (fdc)").let { assertEquals("fdc", it.setCode) }
        // Bracketed codes, either case
        one("1 Sol Ring [FDC]").let { assertEquals("Sol Ring", it.name); assertEquals("fdc", it.setCode) }
        one("1 Sol Ring [fdc]").let { assertEquals("Sol Ring", it.name); assertEquals("fdc", it.setCode) }
        one("1 Sol Ring [FDC] 123").let { assertEquals("fdc", it.setCode); assertEquals("123", it.collectorNumber) }
        // Archidekt
        one("1x Sol Ring (fdc) 123 *F* [Ramp] ^Have,#37d67a^").let {
            assertEquals("Sol Ring", it.name); assertEquals("fdc", it.setCode); assertEquals("123", it.collectorNumber); assertTrue(it.foil)
        }
        // Moxfield with tags; a foil marker before the set
        one("1 Sol Ring (FDC) 123 #Ramp #!Staple").let { assertEquals("fdc", it.setCode); assertEquals("123", it.collectorNumber) }
        one("1 Sol Ring *F* (FDC) 123").let { assertEquals("Sol Ring", it.name); assertEquals("fdc", it.setCode); assertTrue(it.foil) }
        // Odd collector numbers
        one("1 Sol Ring (PLST) C21-263").let { assertEquals("plst", it.setCode); assertEquals("C21-263", it.collectorNumber) }
        one("1 Lightning Bolt (SLD) 1234★").let { assertEquals("1234★", it.collectorNumber) }
        one("1 Delver of Secrets // Insectile Aberration (ISD) 51a").let {
            assertEquals("Delver of Secrets // Insectile Aberration", it.name); assertEquals("51a", it.collectorNumber)
        }
        // Deckstats and MTGGoldfish
        one("1 [FDC#123] Sol Ring").let { assertEquals("Sol Ring", it.name); assertEquals("fdc", it.setCode); assertEquals("123", it.collectorNumber) }
        one("1 [FDC] Sol Ring").let { assertEquals("Sol Ring", it.name); assertEquals("fdc", it.setCode) }
        one("4 Lightning Bolt <retro> [2X2]").let { assertEquals("Lightning Bolt", it.name); assertEquals("2x2", it.setCode) }
        one("1 Forest <251> [THB]").let { assertEquals("Forest", it.name); assertEquals("thb", it.setCode); assertEquals("251", it.collectorNumber) }
        // MTGO: no printing
        one("4 Lightning Bolt").let { assertEquals(null, it.setCode); assertEquals(null, it.collectorNumber) }
    }

    @Test fun printingsInAFullList() {
        val deck = DecklistParser.parse(
            "Commander\n1 Atraxa, Praetors' Voice (2X2) 190 *F*\n\nDeck\n1 Sol Ring (FDC) 123\n1 Sol Ring (CMR) 263\n1 Command Tower (fdc) 351\n",
        )
        assertEquals(listOf("2x2", "fdc", "cmr", "fdc"), deck.map { it.setCode })
        assertEquals("Commander", deck[0].section)
        assertEquals("Atraxa, Praetors' Voice", deck[0].name)
    }

    @Test fun commanderSections() {
        // Archidekt and Moxfield plain text mark the commander inline.
        val marked = DecklistParser.parse("1x Atraxa, Praetors' Voice (ONE) 196 *CMDR*\n1x Sol Ring (CMR) 263 *F*\n1 Thrasios, Triton Hero *CMDR* *F*")
        assertEquals("Commander", marked[0].section)
        assertEquals("Atraxa, Praetors' Voice", marked[0].name)
        assertEquals("one", marked[0].setCode)
        assertEquals("196", marked[0].collectorNumber)
        assertEquals("Main", marked[1].section)
        assertTrue(marked[1].foil)
        assertEquals("Commander", marked[2].section)
        assertEquals("Thrasios, Triton Hero", marked[2].name)
        assertTrue(marked[2].foil)
        // Arena: Commander and Companion headers, then Deck.
        val arena = DecklistParser.parse("Commander\n1 Kenrith, the Returned King (ELD) 303\n\nCompanion\n1 Jegantha, the Wellspring (IKO) 222\n\nDeck\n1 Sol Ring (CMR) 263")
        assertEquals(listOf("Commander", "Companion", "Main"), arena.map { it.section })
        // Oathbreaker
        val oath = DecklistParser.parse("Oathbreaker\n1 Chandra, Torch of Defiance\n\nSignature Spell\n1 Fireball\n\nDeck\n1 Shock")
        assertEquals(listOf("Commander", "Signature", "Main"), oath.map { it.section })
        // Moxfield's "CMDR:" prefix still works, and a card named like a header stays in the main deck.
        assertEquals("Commander", DecklistParser.parse("CMDR: 1 Atraxa, Praetors' Voice").single().section)
        assertEquals("Main", DecklistParser.parse("1 Commander's Sphere").single().section)
    }
}
