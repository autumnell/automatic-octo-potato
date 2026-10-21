package com.mtgscanner.ui.trade

import com.mtgscanner.data.Prices
import com.mtgscanner.data.ScryfallCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TradeGetTest {
    private fun card(id: String, set: String, num: String, finishes: List<String>, usd: String? = "1.00", foil: String? = "5.00") =
        ScryfallCard(id = id, name = "Bolt", set = set, setName = set, collectorNumber = num, rarity = "common", finishes = finishes, prices = Prices(usd = usd, usdFoil = foil))

    @Test fun pricesByFinish() {
        val c = card("a", "m10", "1", listOf("nonfoil", "foil"))
        val item = getItem("k", c)
        assertFalse(item.foil)
        assertEquals(1.0, item.price!!, 0.0)
        val foil = item.withDetails(foil = true)
        assertTrue(foil.foil)
        assertEquals(5.0, foil.price!!, 0.0)
    }

    @Test fun foilOnlyPrintingForcesFoil() {
        val item = getItem("k", card("a", "m10", "1", listOf("nonfoil", "foil")), qty = 3, condition = "LP", lang = "ja")
        val moved = item.withPrinting(card("b", "sld", "99", listOf("foil"), usd = null))
        assertTrue(moved.foil)
        assertEquals(5.0, moved.price!!, 0.0)
        assertEquals(3, moved.qty)
        assertEquals("LP", moved.condition)
        assertEquals("ja", moved.lang)
        assertEquals("k", moved.key)
        assertEquals("SLD #99 · Foil · LP · JA", moved.getLabel())
        // Back to a nonfoil-only printing turns foil off.
        assertFalse(moved.withPrinting(card("c", "lea", "2", listOf("nonfoil"))).foil)
    }

    @Test fun plainLabel() {
        assertEquals("M10 #1", getItem("k", card("a", "m10", "1", listOf("nonfoil", "foil"))).getLabel())
    }
}
