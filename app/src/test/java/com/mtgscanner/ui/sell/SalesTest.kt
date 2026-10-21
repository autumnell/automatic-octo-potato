package com.mtgscanner.ui.sell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SalesTest {
    @Test
    fun detailRoundTrips() {
        val d = Sales.detail(2, 3.5)
        assertEquals("×2 · \$3.50 each", d)
        assertEquals(7.0, Sales.amount(d)!!, 1e-9)
    }

    @Test
    fun earnedCountsOnlySales() {
        val events = listOf(
            "sold" to Sales.detail(1, 10.0),
            "removed" to "×3",
            "sold" to Sales.detail(3, 0.25),
            "edited" to "Sale undone",
            "sold" to "garbled",
        )
        assertEquals(10.75, Sales.earned(events), 1e-9)
    }

    @Test
    fun parsesTypedPrices() {
        assertEquals(3.5, Sales.parsePrice(" \$3,50 ")!!, 1e-9)
        assertEquals(0.0, Sales.parsePrice("0")!!, 1e-9)
        assertNull(Sales.parsePrice("abc"))
        assertNull(Sales.parsePrice("-2"))
        assertNull(Sales.parsePrice(""))
    }
}
