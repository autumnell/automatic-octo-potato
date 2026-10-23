package com.mtgscanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spread-out copies, cheap cards in value boxes, and "Checked 3w ago". */
class BoxCleanupTest {
    private fun row(id: Long, name: String, box: Long, qty: Int = 1, set: String = "mh3", number: String = "1") =
        SpreadCopies.Row(id, name, set, number, box, qty)

    @Test fun spreadGathersIntoTheBoxWithMostCopies() {
        val rows = listOf(row(1, "Bolt", 1), row(2, "Bolt", 2, qty = 3), row(3, "Bolt", 3), row(4, "Duress", 1))
        val g = SpreadCopies.find(rows, listOf(1, 2, 3), byPrinting = false).single()
        assertEquals("Bolt", g.name)
        assertEquals(2L, g.target)
        assertEquals(listOf(1L to 2L, 3L to 2L), g.moves)
        assertEquals(2, g.toMove)
        assertEquals(listOf(2L to 3, 1L to 1, 3L to 1), g.perBox)
    }

    @Test fun spreadTieGoesToTheEarlierBox() {
        val rows = listOf(row(1, "Bolt", 7), row(2, "Bolt", 5))
        assertEquals(5L, SpreadCopies.find(rows, listOf(5, 7), byPrinting = false).single().target)
    }

    @Test fun spreadSkipsBoxesLeftOutAndSingleBoxCards() {
        val rows = listOf(row(1, "Bolt", 1), row(2, "Bolt", 9), row(3, "Bolt", 1, set = "m10"))
        assertTrue(SpreadCopies.find(rows, listOf(1), byPrinting = false).isEmpty())
    }

    @Test fun spreadByPrintingNeedsSameSetAndNumber() {
        val rows = listOf(row(1, "Bolt", 1, set = "m10"), row(2, "Bolt", 2, set = "mh3"), row(3, "Bolt", 2, set = "m10", number = "2"))
        assertTrue(SpreadCopies.find(rows, listOf(1, 2), byPrinting = true).isEmpty())
        assertEquals(1, SpreadCopies.find(rows, listOf(1, 2), byPrinting = false).size)
        val same = rows + row(4, "Bolt", 2, set = "m10")
        val g = SpreadCopies.find(same, listOf(1, 2), byPrinting = true).single()
        assertEquals("m10", g.setCode)
        // One copy each: the earlier box keeps them.
        assertEquals(listOf(4L to 1L), g.moves)
    }

    private fun facts(price: Double?, rarity: String = "rare") = CardFacts("U", rarity, "Instant", emptySet(), false, price, "mh3")

    private data class C(val id: Long, val box: Long, val price: Double?, val rarity: String = "rare", val pinned: Boolean = false)

    @Test fun valueBoxes() {
        assertTrue(CheapInValue.isValueBox(BoxRules(minPrice = 5.0)))
        assertTrue(CheapInValue.isValueBox(BoxRules(rarities = setOf("rare", "mythic"))))
        assertFalse(CheapInValue.isValueBox(BoxRules(rarities = setOf("rare", "common"))))
        assertFalse(CheapInValue.isValueBox(BoxRules()))
    }

    @Test fun cheapCardsInValueBoxesGoToBulk() {
        val rares = BoxInfo(1, BoxRules(rarities = setOf("rare", "mythic")))
        val blue = BoxInfo(2, BoxRules(colors = setOf("U")))
        val bulk = BoxInfo(3, BoxRules())
        val trade = BoxInfo(4, BoxRules(minPrice = 1.0), kind = "trade")
        val boxes = listOf(rares, blue, bulk, trade)
        val policy = SortPolicy(bulkFloor = 0.5, bulkBoxId = 3)
        val cards = listOf(
            C(1, 1, 0.10), // cheap in Rares: moves
            C(2, 1, 2.00), // worth keeping
            C(3, 1, null), // no price yet
            C(4, 2, 0.10), // Blue isn't a value box
            C(5, 4, 0.10), // trade binder
            C(6, 1, 0.10, pinned = true),
        )
        val hits = CheapInValue.find(cards, { it.box }, { facts(it.price, it.rarity) }, boxes, policy, { it.pinned })
        assertEquals(listOf(1L), hits.map { it.item.id })
        assertEquals(3L, hits.single().to)
        assertEquals(1L, hits.single().from)
    }

    @Test fun noBulkFloorFindsNothing() {
        val rares = BoxInfo(1, BoxRules(rarities = setOf("rare")))
        val hits = CheapInValue.find(listOf(C(1, 1, 0.01)), { it.box }, { facts(it.price) }, listOf(rares), SortPolicy())
        assertTrue(hits.isEmpty())
    }

    @Test fun lastCheckedLabels() {
        val day = LastCheckedMath.DAY_MS
        val now = 1000 * day
        assertNull(LastCheckedMath.label(null, now))
        assertEquals("Checked today", LastCheckedMath.label(now - 1000, now))
        assertEquals("Checked 3d ago", LastCheckedMath.label(now - 3 * day, now))
        assertEquals("Checked 3w ago", LastCheckedMath.label(now - 22 * day, now))
        assertEquals("Checked 4mo ago", LastCheckedMath.label(now - 125 * day, now))
        assertEquals("Checked 2y ago", LastCheckedMath.label(now - 800 * day, now))
    }

    @Test fun oldestCheckFirstNeverCheckedBeforeAll() {
        val order = LastCheckedMath.oldestFirst(listOf(1L, 2L, 3L, 4L), { it }, mapOf(1L to 50L, 2L to 10L, 4L to 30L))
        assertEquals(listOf(3L, 2L, 4L, 1L), order)
    }
}
