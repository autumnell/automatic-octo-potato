package com.mtgscanner.ui.search

import org.junit.Assert.assertEquals
import org.junit.Test

class PriceListMathTest {
    private val lines = listOf(
        PricedLine("Sol Ring", 2, 1.50, owned = 2),
        PricedLine("Lightning Bolt", 4, 0.75, owned = 1),
        PricedLine("Odd Promo", 1, null, owned = 0),
        PricedLine("Typo Card", 3, null, found = false),
    )

    @Test fun total() {
        val t = PriceListMath.totals(lines)
        assertEquals(10, t.cards)
        assertEquals(6.0, t.total, 1e-9)
        assertEquals(1, t.unpriced)
        assertEquals(1, t.notFound)
    }

    @Test fun shortCountsOnlyWhatYouLack() {
        val t = PriceListMath.totals(lines)
        assertEquals(4, t.shortCards) // 3 bolts + 1 promo; the typo isn't counted
        assertEquals(2.25, t.shortValue, 1e-9)
        assertEquals(listOf("Lightning Bolt", "Odd Promo"), PriceListMath.short(lines).map { it.name })
    }

    @Test fun owningMoreThanListedIsNotNegative() {
        assertEquals(0, PricedLine("X", 1, 1.0, owned = 5).short)
    }

    @Test fun emptyListTotalsZero() {
        val t = PriceListMath.totals(emptyList())
        assertEquals(0, t.cards)
        assertEquals(0.0, t.total, 0.0)
    }
}
