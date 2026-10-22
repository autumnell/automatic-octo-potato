package com.mtgscanner.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HaveListPageTest {
    private val items = listOf(
        HaveItem("Opt", "dom", "60", 2, 0.25, "U", "https://cards.scryfall.io/normal/front/a.jpg", link = "https://scryfall.com/card/dom/60"),
        HaveItem("Atraxa, Grand Unifier", "one", "196", 1, 40.0, "WUBG", null, foil = true, condition = "LP"),
        HaveItem("<script>alert(1)</script>", "x", "1", 1, null, "", "javascript:alert(1)"),
    )

    @Test fun `page has every card, a total and the controls`() {
        val h = HaveListPage.html("Trade binder", items, showPrices = true, date = "2026-10-22")
        assertTrue(h.startsWith("<!doctype html>"))
        assertTrue(h.contains("<title>Trade binder</title>"))
        assertTrue(h.contains("4 cards · $40.50"))
        assertEquals(3, Regex("class=\"card\"").findAll(h).count())
        assertTrue(h.contains("id=\"q\""))
        assertTrue(h.contains("value=\"price\""))
        assertTrue(h.contains("prefers-color-scheme:dark"))
        assertTrue(h.contains("data-c=\"WUBG\""))
        assertTrue(h.contains("×2 DOM #60"))
        assertTrue(h.contains("ONE #196 · foil · LP"))
        assertTrue(h.contains("$40.00"))
    }

    @Test fun `cards are sorted by name`() {
        val h = HaveListPage.html("t", items, true, "d")
        assertTrue(h.indexOf("data-n=\"atraxa") < h.indexOf("data-n=\"opt"))
    }

    @Test fun `text is escaped and only https pictures and links are used`() {
        val h = HaveListPage.html("A & B", items, true, "d")
        assertFalse(h.contains("<script>alert"))
        assertTrue(h.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
        assertFalse(h.contains("javascript:"))
        assertTrue(h.contains("<title>A &amp; B</title>"))
        assertTrue(h.contains("<a class=\"card\" href=\"https://scryfall.com/card/dom/60\""))
    }

    @Test fun `prices hidden when off`() {
        val h = HaveListPage.html("t", items, showPrices = false, date = "d")
        assertFalse(h.contains("$40"))
        assertFalse(h.contains("value=\"price\""))
        assertTrue(h.contains("4 cards</div>"))
    }

    @Test fun `usd and esc`() {
        assertEquals("$1,234.50", HaveListPage.usd(1234.5))
        assertEquals("&quot;a&#39;", HaveListPage.esc("\"a'"))
    }
}
