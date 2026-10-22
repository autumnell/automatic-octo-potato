package com.mtgscanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckDiffTest {
    @Test fun addedRemovedChangedUnchanged() {
        val old = "4 Lightning Bolt (M10) 146\n2 Duress\n1 Sol Ring (CMR) 263\n1 Fire // Ice\n\nMaybeboard\n1 Opt"
        val new = "4 Lightning Bolt (M10) 146\n1 Sol Ring (CMR) 472\n1 Fire/Ice\n3 Island\n\nMaybeboard\n4 Opt"
        val d = DeckDiffs.of(old, new)
        assertEquals(listOf(DiffLine("island", "Island", 3)), d.added)
        assertEquals(listOf(DiffLine("duress", "Duress", 2)), d.removed)
        assertEquals(listOf(DiffChange("solring", "Sol Ring", 1, "CMR 263", "CMR 472")), d.changed)
        assertEquals(5, d.unchangedCount)
        assertEquals(listOf("Lightning Bolt", "Fire // Ice"), d.unchanged.map { it.name })
    }

    @Test fun countChangesAndSameList() {
        val d = DeckDiffs.of("4 Island\n2 Shock", "2 Island\nSideboard\n3 Shock")
        assertEquals(listOf(DiffLine("island", "Island", 2)), d.removed)
        assertEquals(listOf(DiffLine("shock", "Shock", 1)), d.added)
        assertEquals(4, d.unchangedCount)
        assertTrue(DeckDiffs.of("1 Sol Ring\n4 Island", "4 Island\n1 sol ring").isEmpty)
    }

    @Test fun leavingKeepsWhatTheListStillNeeds() {
        val slot = listOf(
            SlotRow(1, "Island", "dom", "250", 3),
            SlotRow(2, "Island", "thb", "251", 2),
            SlotRow(3, "Shock", null, null, 1),
        )
        // 5 in the deck, the list now needs 3: two go, the printing the list doesn't name first.
        val keep = DeckDiffs.keptPrintings("3 Island (DOM) 250", "island")
        assertEquals(listOf(2L to 2), DeckDiffs.leaving(slot, "island", removed = 2, stillNeeded = 3, keepPrintings = keep))
        assertEquals(listOf(2L to 2, 1L to 1), DeckDiffs.leaving(slot, "island", removed = 3, stillNeeded = 0, keepPrintings = keep))
        // Fewer copies in the deck than the list needs: none leave.
        assertEquals(emptyList<Pair<Long, Int>>(), DeckDiffs.leaving(slot, "shock", removed = 2, stillNeeded = 1))
        assertEquals(3, DeckDiffs.totals("3 Island\nMaybeboard\n1 Opt")["island"])
        assertEquals(null, DeckDiffs.totals("3 Island\nMaybeboard\n1 Opt")["opt"])
    }
}
