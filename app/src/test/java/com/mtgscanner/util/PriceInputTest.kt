package com.mtgscanner.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PriceInputTest {
    private fun p(s: String) = PriceInput.parse(s)

    @Test
    fun plainAndDecimalComma() {
        assertEquals(4.99, p("4.99")!!, 1e-9)
        assertEquals(4.99, p("4,99")!!, 1e-9)
        assertEquals(3.5, p(" \$3,50 ")!!, 1e-9)
        assertEquals(3.5, p("3,5")!!, 1e-9)
        assertEquals(1.5, p("1.5")!!, 1e-9)
        assertEquals(0.0, p("0")!!, 1e-9)
        assertEquals(0.5, p(".5")!!, 1e-9)
        assertEquals(4.0, p("4,")!!, 1e-9)
    }

    @Test
    fun currencySigns() {
        assertEquals(12.0, p("\$12")!!, 1e-9)
        assertEquals(12.0, p("\$ 12")!!, 1e-9)
        assertEquals(4.99, p("€4,99")!!, 1e-9)
        assertEquals(4.99, p("4,99 €")!!, 1e-9)
    }

    @Test
    fun thousands() {
        assertEquals(1200.0, p("1,200")!!, 1e-9)
        assertEquals(1200.5, p("1.200,50")!!, 1e-9)
        assertEquals(1234.5, p("1,234.50")!!, 1e-9)
        assertEquals(1200000.0, p("1,200,000")!!, 1e-9)
        assertEquals(1200000.0, p("1.200.000")!!, 1e-9)
        assertEquals(1200000.5, p("1.200.000,5")!!, 1e-9)
        assertEquals(1200.5, p("1 200,50")!!, 1e-9)
    }

    @Test
    fun zeroCommaIsDecimal() {
        assertEquals(0.125, p("0,125")!!, 1e-9)
        assertEquals(0.5, p("0,50")!!, 1e-9)
    }

    @Test
    fun rejectsNonPrices() {
        assertNull(p(""))
        assertNull(p("  "))
        assertNull(p("abc"))
        assertNull(p("-2"))
        assertNull(p("\$"))
        assertNull(p("."))
        assertNull(p("1,2,3"))
        assertNull(p("12,34,567"))
        assertNull(p("1.2.3"))
        assertNull(p("1,234.5.6"))
        assertNull(p("1e5"))
    }
}
