package com.mtgscanner.ui.collection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeekChangeTest {
    private val weekAgo = "2026-10-15"
    private val oldest = "2026-10-08"

    @Test fun thenIsLastPriceOnOrBeforeWeekAgo() {
        val h = listOf("2026-10-10" to 1.0, "2026-10-14" to 2.0, "2026-10-16" to 3.0)
        assertEquals(2.0, WeekChange.then(h, weekAgo, oldest)!!, 0.0001)
    }

    @Test fun tooOldOrOnlyNewerHistoryCountsAsNone() {
        assertNull(WeekChange.then(listOf("2026-09-01" to 1.0), weekAgo, oldest))
        assertNull(WeekChange.then(listOf("2026-10-20" to 1.0), weekAgo, oldest))
        assertNull(WeekChange.change(null, listOf("2026-10-15" to 1.0), weekAgo, oldest))
    }

    @Test fun changeIsNowMinusThen() {
        assertEquals(1.5, WeekChange.change(3.5, listOf("2026-10-15" to 2.0), weekAgo, oldest)!!, 0.0001)
    }

    @Test fun totalWeighsByQuantityAndSkipsRowsWithoutHistory() {
        val rows = listOf(
            WeekChange.Row(3.0, 2, listOf("2026-10-12" to 2.0)),
            WeekChange.Row(5.0, 1, emptyList()),
            WeekChange.Row(1.0, 3, listOf("2026-10-15" to 1.5)),
        )
        assertEquals(2.0 - 1.5, WeekChange.total(rows, weekAgo, oldest)!!, 0.0001)
        assertNull(WeekChange.total(listOf(WeekChange.Row(5.0, 1, emptyList())), weekAgo, oldest))
    }

    @Test fun labels() {
        assertEquals("—", WeekChange.label(null))
        assertEquals("+2.10", WeekChange.label(2.1))
        assertEquals("−3.05", WeekChange.label(-3.05))
        assertEquals("0.00", WeekChange.label(-0.001))
        assertEquals("+1,041.80", WeekChange.label(1041.8))
        assertEquals("3,418.20", WeekChange.amount(3418.2))
        assertEquals("—", WeekChange.amount(null))
    }
}
