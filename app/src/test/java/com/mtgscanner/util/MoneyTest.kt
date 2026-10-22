package com.mtgscanner.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {
    @Test fun eachCurrency() {
        assertEquals("$1,234.50", Money.text(1234.5, 1100.0, PriceCurrency.USD))
        assertEquals("€1,100.00", Money.text(1234.5, 1100.0, PriceCurrency.EUR))
        assertEquals("$2.10 · €1.90", Money.text(2.1, 1.9, PriceCurrency.BOTH))
    }

    @Test fun missingPrices() {
        assertEquals("--", Money.text(2.0, null, PriceCurrency.EUR))
        assertEquals("$2.00", Money.text(2.0, null, PriceCurrency.BOTH))
        assertEquals("€3.00", Money.text(null, 3.0, PriceCurrency.BOTH))
        assertEquals("--", Money.text(null, null, PriceCurrency.USD))
    }

    @Test fun negativeEuros() {
        assertEquals("-€0.50", Money.eur(-0.5))
    }

    @Test fun unknownSettingIsUsd() {
        assertEquals(PriceCurrency.USD, PriceCurrency.of(null))
        assertEquals(PriceCurrency.USD, PriceCurrency.of("GBP"))
        assertEquals(PriceCurrency.BOTH, PriceCurrency.of("BOTH"))
    }
}
