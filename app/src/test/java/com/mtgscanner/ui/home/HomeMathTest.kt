package com.mtgscanner.ui.home

import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.PricePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeMathTest {
    private fun card(id: String, box: Long, qty: Int = 1, price: Double? = 1.0, rarity: String = "common", proxy: Boolean = false) = CollectionCard(
        scryfallId = id, oracleId = null, name = id, setCode = "M10", setName = "Magic 2010",
        collectorNumber = "1", rarity = rarity, typeLine = "Instant", manaCost = "", cmc = 1.0, colors = "R",
        colorIdentity = "R", oracleText = "", tags = "", priceUsd = price, priceUsdFoil = null, imageUrl = null,
        smallImageUrl = null, scryfallUri = "", lang = "en", releasedAt = null, artist = null, foil = false, quantity = qty,
        boxId = box, proxy = proxy, addedAt = 0, updatedAt = 0,
    )

    @Test fun boxValuesCarryPricesForward() {
        val cards = listOf(card("a", 1, qty = 2), card("b", 2, price = 3.0))
        val points = listOf(
            PricePoint("a", "2026-10-01", 1.0, null),
            PricePoint("a", "2026-10-03", 2.0, null),
            PricePoint("x", "2026-10-02", 9.0, null),
        )
        val r = HomeMath.boxValues(cards, points)
        assertEquals(listOf("2026-10-01", "2026-10-02", "2026-10-03"), r.days)
        assertEquals(listOf(2.0, 2.0, 4.0), r.values[1L])
        // No history: today's price every day.
        assertEquals(listOf(3.0, 3.0, 3.0), r.values[2L])
    }

    @Test fun moversCompareWithAWeekAgo() {
        val cards = listOf(card("a", 1, qty = 2), card("a", 2, qty = 1), card("b", 1))
        val points = listOf(
            PricePoint("a", "2026-10-01", 1.0, null),
            PricePoint("a", "2026-10-10", 3.0, null),
            PricePoint("b", "2026-10-09", 1.0, null),
            PricePoint("b", "2026-10-10", 1.0, null),
        )
        val m = HomeMath.movers(cards, points, weekAgo = "2026-10-03")
        assertEquals(1, m.size)
        assertEquals(6.0, m[0].change, 0.001)
    }

    @Test fun bulkCountsCheapCommonsAndUncommons() {
        val cards = listOf(
            card("a", 1, qty = 500, price = 0.1),
            card("b", 1, qty = 500, price = 0.2, rarity = "uncommon"),
            card("c", 1, qty = 10, price = 2.0),
            card("d", 1, qty = 10, price = 0.1, rarity = "rare"),
            card("e", 1, qty = 10, price = 0.1, proxy = true),
        )
        val b = HomeMath.bulk(cards, 5.0)
        assertEquals(1000, b.cards)
        assertTrue(kotlin.math.abs(b.value - 5.0) < 0.001)
    }
}
