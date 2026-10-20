package com.mtgscanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtrasFinderTest {
    private fun row(id: Long, key: String, qty: Int, box: Long = 1, at: Long = id, type: String = "Instant", price: Double? = 1.0) =
        ExtraRow(id, key, key, qty, box, at, type, price)

    @Test fun movesNewestCopiesOverTheLimit() {
        val rows = listOf(row(1, "bolt", 3, at = 1), row(2, "bolt", 3, at = 5), row(3, "shock", 4))
        val found = ExtrasFinder.find(rows, keep = 4, skipBoxIds = emptySet())
        assertEquals(1, found.size)
        assertEquals(2, found[0].extra)
        assertEquals(listOf(2L to 2), found[0].moves)
        assertEquals(2.0, found[0].extraValue, 0.001)
    }

    @Test fun ignoresTradeBindersBasicsAndSingletonLimit() {
        val rows = listOf(
            row(1, "bolt", 2), row(2, "bolt", 3, box = 9),
            row(3, "forest", 30, type = "Basic Land — Forest"),
        )
        assertTrue(ExtrasFinder.find(rows, keep = 4, skipBoxIds = setOf(9)).isEmpty())
        val singleton = ExtrasFinder.find(rows, keep = 1, skipBoxIds = setOf(9))
        assertEquals(listOf("bolt"), singleton.map { it.key })
        assertEquals(1, singleton[0].extra)
    }

    @Test fun skipsTradeDeckAndPolicyBoxes() {
        val boxes = listOf(1L to "box", 2L to "deck", 3L to "trade", 4L to "binder", 5L to "box")
        val skipped = ExtrasFinder.skippedBoxes(boxes, SortPolicy(playsetLimit = 4, extrasBoxId = 4, bulkFloor = 0.5, bulkBoxId = 5))
        assertEquals(setOf(2L, 3L, 4L, 5L), skipped)
        val rows = listOf(row(1, "bolt", 4), row(2, "bolt", 4, box = 2))
        assertTrue(ExtrasFinder.find(rows, keep = 4, skipBoxIds = skipped).isEmpty())
    }
}
