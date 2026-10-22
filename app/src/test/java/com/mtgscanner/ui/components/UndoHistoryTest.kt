package com.mtgscanner.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoHistoryTest {
    @Test fun newestFirstAndCappedAtMax() {
        val h = UndoHistory<String>(max = 3)
        (1..5).forEach { h.record("a$it", it * 1000L, "r$it") }
        assertEquals(listOf("a5", "a4", "a3"), h.entries.map { it.label })
    }

    @Test fun undoingNewestTakesOnlyIt() {
        val h = UndoHistory<String>()
        h.record("a", 0, "ra")
        val b = h.record("b", 0, "rb")
        assertEquals(0, h.newerThan(b))
        assertEquals(listOf("rb"), h.takeThrough(b).map { it.restore })
        assertEquals(listOf("a"), h.entries.map { it.label })
    }

    @Test fun undoingOlderUndoesNewerFirst() {
        val h = UndoHistory<String>()
        val a = h.record("a", 0, "ra")
        h.record("b", 0, "rb")
        h.record("c", 0, "rc")
        assertEquals(2, h.newerThan(a))
        assertEquals(listOf("rc", "rb", "ra"), h.takeThrough(a).map { it.restore })
        assertTrue(h.entries.isEmpty())
    }

    @Test fun middleLeavesOlderInPlace() {
        val h = UndoHistory<String>()
        h.record("a", 0, "ra")
        val b = h.record("b", 0, "rb")
        h.record("c", 0, "rc")
        assertEquals(listOf("rc", "rb"), h.takeThrough(b).map { it.restore })
        assertEquals(listOf("a"), h.entries.map { it.label })
    }

    @Test fun goneIdsDoNothing() {
        val h = UndoHistory<String>(max = 1)
        val a = h.record("a", 0, "ra")
        h.record("b", 0, "rb")
        assertNull(h.newerThan(a))
        assertTrue(h.takeThrough(a).isEmpty())
        assertEquals(1, h.entries.size)
        val b = h.entries.single().id
        h.takeThrough(b)
        assertTrue(h.takeThrough(b).isEmpty())
    }

    @Test fun ago() {
        assertEquals("just now", UndoHistory.ago(30_000, 0))
        assertEquals("just now", UndoHistory.ago(0, 5_000))
        assertEquals("3 min ago", UndoHistory.ago(200_000, 0))
        assertEquals("2 h ago", UndoHistory.ago(2 * 3_600_000L + 5, 0))
        assertEquals("1 day ago", UndoHistory.ago(86_400_000L, 0))
        assertEquals("3 days ago", UndoHistory.ago(3 * 86_400_000L, 0))
    }
}
