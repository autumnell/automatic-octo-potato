package com.mtgscanner.data

import com.mtgscanner.data.PrintingLookup.Wanted
import com.mtgscanner.scan.DeckText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetPrintingsTest {
    private fun card(
        number: String, border: String = "black", frame: List<String> = emptyList(), promo: List<String> = emptyList(),
        fullArt: Boolean = false,
    ) = ScryfallCard(
        id = "id$number", name = "Inverted Iceberg // Iceberg Titan", set = "lci", setName = "The Lost Caverns of Ixalan",
        collectorNumber = number, rarity = "uncommon", borderColor = border, frameEffects = frame, fullArt = fullArt,
        promo = promo.isNotEmpty(), promoTypes = promo,
    )

    @Test fun picksTheNormalPrinting() {
        val normal = card("60")
        val borderless = card("287", border = "borderless")
        val showcase = card("330", frame = listOf("showcase"))
        val extended = card("350", frame = listOf("extendedart"))
        val prerelease = card("60s", promo = listOf("prerelease"))
        assertEquals("60", SetPrintings.regular(listOf(borderless, showcase, prerelease, normal, extended))?.collectorNumber)
        // Only variants: the lowest number.
        assertEquals("287", SetPrintings.regular(listOf(showcase, borderless))?.collectorNumber)
        assertEquals(null, SetPrintings.regular(emptyList()))
    }

    @Test fun labels() {
        assertEquals("Borderless", SetPrintings.label(card("287", border = "borderless").treatments))
        assertEquals("Showcase", SetPrintings.label("showcase,promo"))
        assertEquals("Extended art", SetPrintings.label(listOf("extendedart")))
        assertEquals(null, SetPrintings.label(card("60").treatments))
        assertEquals(null, SetPrintings.label(""))
    }

    @Test fun onlySetOnlyLinesAreOpen() {
        assertTrue(SetPrintings.setOnly(Wanted("Sol Ring", "CMR")))
        assertFalse(SetPrintings.setOnly(Wanted("Sol Ring", "CMR", "472")))
        assertFalse(SetPrintings.setOnly(Wanted("Sol Ring")))
        assertFalse(SetPrintings.setOnly(Wanted("Sol Ring", "CMR", id = "abc")))
    }

    @Test fun queriesBatchByOracleAndSet() {
        val q = SetPrintings.queries(listOf("o1" to "lci", "o2" to "eld", "o1" to "lci"), perQuery = 12)
        assertEquals(listOf("(oracleid:o1 e:lci) or (oracleid:o2 e:eld)"), q)
        assertEquals(2, SetPrintings.queries((1..13).map { "o$it" to "lci" }).size)
    }

    @Test fun printingLabelOnTheRow() {
        val l = DeckCheckLine("Inverted Iceberg // Iceberg Titan", "Main", 1, 0, emptyList(), null, null, null,
            setCode = "lci", collectorNumber = null, shownNumber = "287", variant = "Borderless")
        assertEquals("LCI #287 · Borderless", l.printing)
        assertEquals(null, l.copy(setCode = null).printing)
    }

    @Test fun pickedPrintingIsSavedIntoTheList() {
        val text = "Commander\n1 Hylda of the Icy Crown (WOE) 206 *CMDR*\n\nDeck\n1 Inverted Iceberg // Iceberg Titan (LCI)\nSB: 2x Queen of Ice (ELD) 44 *F*\n1 Sol Ring"
        val a = DeckText.setPrinting(text, "Inverted Iceberg // Iceberg Titan", "lci", null, "lci", "287")
        assertEquals("1 Inverted Iceberg // Iceberg Titan (LCI) 287", a.lines()[4])
        // Matched by any form of the name; count, "SB:" and foil mark stay.
        val b = DeckText.setPrinting(a, "Queen of Ice // Rage of Winter", "eld", "44", "eld", "375")
        assertEquals("SB: 2x Queen of Ice (ELD) 375 *F*", b.lines()[5])
        // A line with no printing gets one; the rest is untouched.
        val c = DeckText.setPrinting(b, "Sol Ring", null, null, "cmr", "472")
        assertEquals("1 Sol Ring (CMR) 472", c.lines()[6])
        assertEquals(text.lines().take(4), c.lines().take(4))
        val d = DeckText.setPrinting(text, "Hylda of the Icy Crown", "woe", "206", "woe", "300")
        assertEquals("1 Hylda of the Icy Crown (WOE) 300 *CMDR*", d.lines()[1])
    }
}
