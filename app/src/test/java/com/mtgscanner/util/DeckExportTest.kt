package com.mtgscanner.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DeckExportTest {
    @Test fun line() {
        assertEquals("1 Sol Ring (CMR) 263", DeckExport.line(DeckLine(1, "Sol Ring", "cmr", "263")))
        assertEquals("2 Duress (M19)", DeckExport.line(DeckLine(2, "Duress", "m19")))
        assertEquals("4 Lightning Bolt", DeckExport.line(DeckLine(4, "Lightning Bolt")))
        assertEquals("1 Fire // Ice (MH2) 290 *F*", DeckExport.line(DeckLine(1, "Fire // Ice", "mh2", "290", foil = true)))
    }

    @Test fun sections() {
        val lines = listOf(
            DeckLine(1, "Sol Ring", "cmr", "263"),
            DeckLine(1, "Atraxa, Praetors' Voice", "one", "196", section = "Commander"),
            DeckLine(1, "Duress", section = "Sideboard"),
            DeckLine(1, "Treasure", section = "Tokens"),
            DeckLine(1, "Rhystic Study", section = "Maybeboard"),
            DeckLine(1, "Lurrus of the Dream-Den", section = "Companion"),
        )
        assertEquals(
            "COMMANDER:\n1 Atraxa, Praetors' Voice (ONE) 196\n\nCOMPANION:\n1 Lurrus of the Dream-Den\n\n" +
                "1 Sol Ring (CMR) 263\n\nSIDEBOARD:\n1 Duress\n\nMAYBEBOARD:\n1 Rhystic Study",
            DeckExport.moxfield(lines),
        )
        assertEquals("", DeckExport.moxfield(emptyList()))
    }

    @Test fun commanderInMain() {
        val lines = listOf(DeckLine(1, "Sol Ring"), DeckLine(1, "Krenko, Mob Boss"))
        assertEquals("COMMANDER:\n1 Krenko, Mob Boss\n\n1 Sol Ring", DeckExport.moxfield(lines, listOf("Krenko, Mob Boss")))
    }

    @Test fun readsBack() {
        val text = "Commander\n1 Atraxa, Praetors' Voice (ONE) 196\n\n4 Lightning Bolt\n1 Sol Ring (CMR) 263 *F*\n" +
            "1 Fire/Ice\n\nSideboard\n2 Duress\n\nMaybeboard\n1 Rhystic Study\n\nSignature Spell\n1 Shock"
        val lines = DecklistParser.parse(text)
        val out = DeckExport.moxfield(lines)
        assertEquals(lines.sortedBy { it.name }, DecklistParser.parse(out).sortedBy { it.name })
    }
}
