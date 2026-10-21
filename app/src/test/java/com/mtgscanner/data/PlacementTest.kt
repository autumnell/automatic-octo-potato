package com.mtgscanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacementTest {
    @Test fun binderLabels() {
        assertNull(Binder.label(0))
        assertEquals("Page 1 · Slot 1", Binder.label(1))
        assertEquals("Page 1 · Slot 9", Binder.label(9))
        assertEquals("Page 4 · Slot 7", Binder.label(34))
    }

    @Test fun numberSlotsAfterTheLastUsedInNameOrder() {
        data class E(val id: Long, val name: String, val slot: Int)
        val items = listOf(E(1, "Zur", 0), E(2, "Abzan", 3), E(3, "Bolt", 0), E(4, "Dup", 3), E(5, "Opt", 5))
        val slots = Binder.numberSlots(items, { it.id }, { it.name }, { it.slot })
        // 3 and 5 are taken; the duplicate 3 and the two unplaced cards go after 5, by name.
        assertEquals(mapOf(3L to 6, 4L to 7, 1L to 8), slots)
    }

    @Test fun moveListText() {
        val lines = listOf(
            MoveLine(2, "Opt", "eld", "Bulk", "Blue"),
            MoveLine(1, "Counterspell", "mh2", "Bulk", "Blue", "U"),
            MoveLine(1, "Shock", "m19", "Blue", "Red"),
        )
        val text = MoveLists.text(lines, done = setOf(0))
        assertTrue(text.startsWith("Move list (4 cards)"))
        assertTrue(text.contains("Bulk → Blue\n☐ Counterspell (MH2) · U\n☑ 2× Opt (ELD)\n"))
        assertTrue(text.contains("Blue → Red\n☐ Shock (M19)\n"))
    }

    // Item 85: one pocket per copy.
    @Test fun pocketPerCopy() {
        data class E(val id: Long, val name: String, val slot: Int, val qty: Int)
        val items = listOf(E(1, "Bolt", 1, 3), E(2, "Opt", 2, 1), E(3, "Zap", 0, 2))
        // Bolt takes 1–3, so Opt at 2 overlaps and gets renumbered after Bolt, by name with Zap.
        val slots = Binder.numberSlots(items, { it.id }, { it.name }, { it.slot }, { it.qty })
        assertEquals(mapOf(2L to 4, 3L to 5), slots)
        val pockets = Binder.pockets(listOf(E(1, "Bolt", 8, 3)), { it.slot }, { it.qty })
        assertEquals(setOf(8, 9, 10), pockets.keys)
        assertEquals("Page 1 · Slots 1–3", Binder.label(1, 3))
        assertEquals("Page 1 · Slot 8 to Page 2 · Slot 1", Binder.label(8, 3))
        assertEquals("Page 1 · Slot 4", Binder.label(4, 1))
        assertEquals(1, Binder.span(4, perCopy = false))
        assertEquals(4, Binder.span(4, perCopy = true))
    }
}
