package com.mtgscanner.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneOffset

class MoneyMathTest {
    private fun at(y: Int, m: Int, d: Int) = java.time.LocalDate.of(y, m, d).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test fun groupsByMonth() {
        val months = MoneyMath.months(
            added = listOf(at(2026, 9, 3) to 10.0, at(2026, 9, 30) to 5.0, at(2026, 10, 1) to 2.0, at(2025, 1, 1) to 99.0),
            sales = listOf(at(2026, 10, 20) to 7.5),
            snapshots = listOf("2026-08-15" to 100.0, "2026-09-10" to 120.0, "2026-09-28" to 130.0, "bad" to 1.0),
            now = YearMonth.of(2026, 10), zone = ZoneOffset.UTC, count = 3,
        )
        assertEquals(listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10)), months.map { it.month })
        assertEquals(15.0, months[1].spent, 0.001)
        assertEquals(2.0, months[2].spent, 0.001)
        assertEquals(7.5, months[2].earned, 0.001)
        assertEquals(100.0, months[0].worth!!, 0.001)
        assertEquals(130.0, months[1].worth!!, 0.001)
        // No value recorded in October yet: the last one carries over.
        assertEquals(130.0, months[2].worth!!, 0.001)
    }

    @Test fun nothingYet() {
        val months = MoneyMath.months(emptyList(), emptyList(), emptyList(), YearMonth.of(2026, 10))
        assertEquals(6, months.size)
        assertNull(months.last().worth)
        assertFalse(MoneyMath.hasData(months))
    }
}
