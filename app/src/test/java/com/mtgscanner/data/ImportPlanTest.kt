package com.mtgscanner.data

import com.mtgscanner.data.db.Box
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportPlanTest {
    private fun card(colors: String = "U", price: Double? = 1.0) =
        CardFacts(colors, "rare", "Instant", emptySet(), false, price, "mh3")

    private fun item(colors: String = "U", oracle: String? = "o1", qty: Int = 1, price: Double? = 1.0) =
        ImportPlanner.Item(card(colors, price), oracle, qty)

    private val blue = BoxInfo(1, BoxRules(colors = setOf("U")))
    private val red = BoxInfo(2, BoxRules(colors = setOf("R")))
    private val extras = BoxInfo(3, BoxRules(colors = setOf("G")))

    @Test fun autoSortUsesBoxRulesAndNewBoxForLeftovers() {
        val t = ImportPlanner.targets(listOf(item("U"), item("R"), item("B"), null), Box.AUTO_SORT, listOf(blue, red), SortPolicy()) { 0 }
        assertEquals(listOf(1L, 2L, ImportPlan.NEW_BOX, null), t)
    }

    @Test fun fixedBoxAndNewBoxChoices() {
        val items = listOf(item("U"), null, item("R"))
        assertEquals(listOf(2L, null, 2L), ImportPlanner.targets(items, 2L, listOf(blue, red), SortPolicy()) { 0 })
        assertEquals(listOf(0L, null, 0L), ImportPlanner.targets(items, ImportPlan.NEW_BOX, listOf(blue, red), SortPolicy()) { 0 })
    }

    @Test fun boxFillsUpDuringTheImport() {
        val small = blue.copy(capacity = 3, count = 1)
        val alsoBlue = BoxInfo(4, BoxRules(colors = setOf("U")))
        val t = ImportPlanner.targets(listOf(item(qty = 2, oracle = "a"), item(oracle = "b")), Box.AUTO_SORT, listOf(small, alsoBlue), SortPolicy()) { 0 }
        assertEquals(listOf(1L, 4L), t)
    }

    @Test fun playsetLimitCountsEarlierRows() {
        val p = SortPolicy(playsetLimit = 4, extrasBoxId = 3)
        // 2 owned already; the first row of 2 reaches the limit, so the next row goes to extras.
        val t = ImportPlanner.targets(listOf(item(qty = 2), item(qty = 1)), Box.AUTO_SORT, listOf(blue, extras), p) { 2 }
        assertEquals(listOf(1L, 3L), t)
    }

    @Test fun perBoxGroupsInFirstSeenOrder() {
        assertEquals(listOf(2L to 5, 1L to 1), ImportPlanner.perBox(listOf(2L, null, 1L, 2L), listOf(4, 9, 1, 1)))
    }
}
