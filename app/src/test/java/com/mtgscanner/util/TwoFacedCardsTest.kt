package com.mtgscanner.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cards with two halves written the ways deck sites and game clients export them. */
class TwoFacedCardsTest {
    @Test fun keyTreatsEveryFormAsOneCard() {
        val forms = listOf("Fire // Ice", "Fire//Ice", "Fire/Ice", "Fire / Ice", "Fire", "fire // ice", " FIRE ")
        assertEquals(1, forms.map { CardNames.key(it) }.distinct().size)
        val dfc = listOf("Inverted Iceberg // Iceberg Titan", "Inverted Iceberg", "Inverted Iceberg/Iceberg Titan")
        assertEquals(1, dfc.map { CardNames.key(it) }.distinct().size)
        assertEquals(CardNames.key("Queen of Ice // Rage of Winter"), CardNames.key("Queen of Ice"))
        assertEquals(CardNames.key("Lim-Dûl's Vault"), CardNames.key("Lim-Dul's Vault"))
        assertNotEquals(CardNames.key("Fire // Ice"), CardNames.key("Ice"))
        assertNotEquals(CardNames.key("Lightning Bolt"), CardNames.key("Lightning Helix"))
    }

    @Test fun fullAndFront() {
        assertEquals("Fire // Ice", CardNames.full("Fire/Ice"))
        assertEquals("Fire // Ice", CardNames.full("Fire / Ice"))
        assertEquals("Who // What // When // Where // Why", CardNames.full("Who/What/When/Where/Why"))
        assertEquals("Lightning Bolt", CardNames.full(" Lightning Bolt "))
        assertEquals("Queen of Ice", CardNames.front("Queen of Ice // Rage of Winter"))
        assertTrue(CardNames.hasFaces("Fire/Ice"))
        assertFalse(CardNames.hasFaces("Inverted Iceberg"))
    }

    private fun one(line: String) = DecklistParser.parse(line).single()

    @Test fun moxfieldExport() {
        // Moxfield "Export for Moxfield": full name, set, number, finish marks.
        one("1 Inverted Iceberg // Iceberg Titan (LCI) 60").let {
            assertEquals("Inverted Iceberg // Iceberg Titan", it.name); assertEquals("lci", it.setCode); assertEquals("60", it.collectorNumber)
        }
        one("1 Queen of Ice // Rage of Winter (ELD) 44 *F*").let {
            assertEquals("Queen of Ice // Rage of Winter", it.name); assertEquals("44", it.collectorNumber); assertTrue(it.foil)
        }
        one("1 Esika, God of the Tree // The Prismatic Bridge (KHM) 168 *E*").let { assertTrue(it.foil); assertEquals("168", it.collectorNumber) }
    }

    @Test fun arenaExport() {
        // Arena writes only the front face.
        val lines = DecklistParser.parse("Deck\n1 Inverted Iceberg (LCI) 60\n1 Queen of Ice (ELD) 44\n1 Fable of the Mirror-Breaker (NEO) 141")
        assertEquals(listOf("Inverted Iceberg", "Queen of Ice", "Fable of the Mirror-Breaker"), lines.map { it.name })
        assertEquals(CardNames.key("Queen of Ice // Rage of Winter"), CardNames.key(lines[1].name))
    }

    @Test fun mtgoExport() {
        // MTGO's .txt: "Fire/Ice", no printings.
        val lines = DecklistParser.parse("4 Fire/Ice\n2 Wear/Tear\n1 Delver of Secrets")
        assertEquals(listOf("Fire // Ice", "Wear // Tear", "Delver of Secrets"), lines.map { it.name })
        assertEquals(4, lines[0].quantity)
    }

    @Test fun archidektExport() {
        one("1x Queen of Ice // Rage of Winter (eld) 44 [Removal]").let {
            assertEquals("Queen of Ice // Rage of Winter", it.name); assertEquals("eld", it.setCode); assertEquals("44", it.collectorNumber)
        }
        one("1x Fire / Ice (mh2) 290 *F* [Burn,Draw] ^Have,#37d67a^").let {
            assertEquals("Fire // Ice", it.name); assertEquals("290", it.collectorNumber); assertTrue(it.foil)
        }
    }

    @Test fun numberInsideBrackets() {
        one("1 Inverted Iceberg [LCI:287]").let { assertEquals("lci", it.setCode); assertEquals("287", it.collectorNumber); assertEquals("Inverted Iceberg", it.name) }
        one("1 Sol Ring (CMR#472)").let { assertEquals("cmr", it.setCode); assertEquals("472", it.collectorNumber) }
        one("1 Sol Ring [Ramp]").let { assertEquals("Sol Ring", it.name); assertEquals(null, it.setCode) }
    }

    @Test fun sameCardDifferentFormsMerge() {
        val lines = DecklistParser.parse("1 Fire/Ice\n1 Fire // Ice")
        assertEquals(2, lines.single().quantity)
    }
}
