package com.mtgscanner.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareListTest {
    private fun money(v: Double) = "$" + String.format(java.util.Locale.US, "%.2f", v)

    private val lines = listOf(
        ShareLine("Opt", 2, "dom", "60", 0.25),
        ShareLine("Atraxa, Grand Unifier", 1, "one", "196", 40.0, foil = true, condition = "LP"),
        ShareLine("Sol Ring", 1, proxy = true),
    )

    @Test fun `text lists every line and the total`() {
        val t = ShareList.text("Trade binder", lines, showPrices = true, money = ::money)
        assertTrue(t.startsWith("Trade binder\n\n"))
        assertTrue(t.contains("2× Opt (DOM #60) · $0.25\n"))
        assertTrue(t.contains("1× Atraxa, Grand Unifier (ONE #196) foil · $40.00 LP\n"))
        assertTrue(t.contains("1× Sol Ring\n"))
        assertTrue(t.contains("Total: 4 cards · $40.50"))
        assertTrue(t.endsWith("Shared from Grimoire"))
    }

    @Test fun `text without prices has no prices`() {
        val t = ShareList.text("Wishlist", lines.take(1), showPrices = false, money = ::money)
        assertTrue(t.contains("2× Opt (DOM #60)\n"))
        assertFalse(t.contains("$"))
        assertTrue(t.contains("Total: 2 cards\n"))
    }

    @Test fun `web page leaves proxies out`() {
        val items = ShareList.haveItems(lines)
        assertEquals(listOf("Opt", "Atraxa, Grand Unifier"), items.map { it.name })
        assertTrue(items[1].foil)
    }

    @Test fun `cart merges names in the store's format`() {
        val cart = ShareList.cart(listOf(ShareLine("Opt", 2), ShareLine("opt", 1), ShareLine("Fire // Ice", 1)), BuyStore.CARDMARKET)
        assertEquals("3x Opt\n1x Fire // Ice", cart)
    }
}
