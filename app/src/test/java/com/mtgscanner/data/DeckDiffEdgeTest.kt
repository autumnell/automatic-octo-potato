package com.mtgscanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Edges of the deck list diff: faces, printings, foils, sections, and which copies leave. */
class DeckDiffEdgeTest {
    @Test fun frontFaceMatchesFullName() {
        assertTrue(DeckDiffs.of("1 Delver of Secrets // Insectile Aberration", "1 Delver of Secrets").isEmpty)
        assertTrue(DeckDiffs.of("1 Fire / Ice", "1 fire//ice").isEmpty)
    }

    @Test fun setCodeCaseDoesNotMatter() {
        assertTrue(DeckDiffs.of("1 Sol Ring (cmr) 263", "1 Sol Ring (CMR) 263").isEmpty)
    }

    @Test fun sectionsCountButMaybeboardAndTokensDont() {
        assertTrue(DeckDiffs.of("Commander\n1 Atraxa, Praetors' Voice\n\nDeck\n1 Sol Ring", "1 Sol Ring\nSideboard\n1 Atraxa, Praetors' Voice").isEmpty)
        val d = DeckDiffs.of("1 Sol Ring", "1 Sol Ring\nMaybeboard\n2 Opt\nTokens\n1 Treasure")
        assertTrue(d.isEmpty)
        assertEquals(1, d.unchangedCount)
    }

    @Test fun printingSwapAmongCopies() {
        val d = DeckDiffs.of("2 Island (DOM) 250\n2 Island (THB) 251", "4 Island (DOM) 250")
        assertEquals(listOf(DiffChange("island", "Island", 2, "THB 251", "DOM 250")), d.changed)
        // Two printings before, so the unchanged copies name none.
        assertEquals(listOf(DiffLine("island", "Island", 2, null)), d.unchanged)
        assertTrue(d.added.isEmpty() && d.removed.isEmpty())
    }

    @Test fun moreCopiesInANewPrinting() {
        val d = DeckDiffs.of("1 Sol Ring", "3 Sol Ring (CMR) 263")
        assertEquals(listOf(DiffLine("solring", "Sol Ring", 2, "CMR 263")), d.added)
        assertEquals(listOf(DiffChange("solring", "Sol Ring", 1, null, "CMR 263")), d.changed)
        assertEquals(2, d.addedCount)
        assertEquals(1, d.changedCount)
    }

    @Test fun foilOnlyChangeSaysSo() {
        val d = DeckDiffs.of("1 Sol Ring (CMR) 263", "1 Sol Ring (CMR) 263 *F*")
        assertEquals(listOf(DiffChange("solring", "Sol Ring", 1, "CMR 263", "CMR 263 foil")), d.changed)
        val plain = DeckDiffs.of("1 Opt", "1 Opt *F*")
        assertEquals(listOf(DiffChange("opt", "Opt", 1, null, "Foil")), plain.changed)
    }

    @Test fun orderFollowsTheNewListThenTheOld() {
        val d = DeckDiffs.of("1 Duress\n1 Opt", "1 Shock\n1 Bolt")
        assertEquals(listOf("Shock", "Bolt"), d.added.map { it.name })
        assertEquals(listOf("Duress", "Opt"), d.removed.map { it.name })
    }

    @Test fun totalsAddUpSections() {
        val t = DeckDiffs.totals("2 Island (DOM) 250\n1 Island\nSideboard\n1 Island")
        assertEquals(4, t["island"])
    }

    @Test fun leavingSkipsEmptyRowsAndKeepsSetOnlyPrintings() {
        val slot = listOf(
            SlotRow(1, "Island", "dom", "250", 2),
            SlotRow(2, "Island", "thb", "251", 0),
            SlotRow(3, "Island", "dom", "260", 1),
            SlotRow(4, "Island", "znr", "381", 1),
        )
        // The list names DOM without a number: every DOM row is kept back, ZNR goes first.
        val keep = DeckDiffs.keptPrintings("2 Island (DOM)", "island")
        assertEquals(listOf(4L to 1, 1L to 1), DeckDiffs.leaving(slot, "island", removed = 2, stillNeeded = 2, keepPrintings = keep))
        // Never more than the deck holds.
        assertEquals(listOf(1L to 2, 3L to 1, 4L to 1), DeckDiffs.leaving(slot, "island", removed = 9, stillNeeded = 0))
        assertEquals("cmr|263", DeckDiffs.printingKey("CMR", "263"))
        assertEquals("cmr|", DeckDiffs.printingKey("cmr", null))
    }
}
