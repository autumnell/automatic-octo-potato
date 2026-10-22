package com.mtgscanner.ui.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanSummaryTest {
    private fun line(name: String, price: Double?, count: Int = 1, box: String = "Rares") =
        SummaryLine(name, "img/$name", price, count, box)

    @Test fun emptyOrZeroCountGivesNoSummary() {
        assertNull(ScanSummary.of(emptyList()))
        assertNull(ScanSummary.of(listOf(line("A", 1.0, count = 0))))
    }

    @Test fun totalsCountAndValue() {
        val s = ScanSummary.of(listOf(line("A", 2.5, 2), line("B", null, 3), line("C", 0.1)))!!
        assertEquals(6, s.cards)
        assertEquals(5.1, s.value, 1e-9)
    }

    @Test fun bestIsPriciestCopyNotPriciestLine() {
        val s = ScanSummary.of(listOf(line("Bulk", 0.5, 40), line("Rare", 12.0), line("Unknown", null)))!!
        assertEquals("Rare", s.best?.name)
    }

    @Test fun noPricesMeansNoBest() {
        assertNull(ScanSummary.of(listOf(line("A", null), line("B", 0.0)))!!.best)
    }

    @Test fun boxesMostFirstThenByName() {
        val s = ScanSummary.of(
            listOf(line("A", 1.0, 2, "Blue"), line("B", 1.0, 1, "Red"), line("C", 1.0, 2, "Bulk"), line("D", 1.0, 1, "Blue"), line("E", 1.0, 1, "")),
        )!!
        assertEquals(listOf("Blue" to 3, "Bulk" to 2, ScanSummary.NO_BOX to 1, "Red" to 1), s.boxes)
    }
}
