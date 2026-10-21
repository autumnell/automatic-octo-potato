package com.mtgscanner.ui.home

import com.mtgscanner.data.db.CollectionCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaidAndTilesTest {
    private fun card(
        id: String, qty: Int = 1, price: Double? = 1.0, paid: Double? = null, proxy: Boolean = false,
        rarity: String = "common", type: String = "Instant", text: String = "",
        sale: Boolean = false, signed: Boolean = false, graded: String = "",
    ) = CollectionCard(
        scryfallId = id, oracleId = id, name = id, setCode = "m10", setName = "M10",
        collectorNumber = "1", rarity = rarity, typeLine = type, manaCost = "", cmc = 1.0, colors = "R",
        colorIdentity = "R", oracleText = text, tags = "", priceUsd = price, priceUsdFoil = null, imageUrl = null,
        smallImageUrl = null, scryfallUri = "", lang = "en", releasedAt = null, artist = null, foil = false, quantity = qty,
        boxId = 1, proxy = proxy, purchasePrice = paid, forSale = sale, signed = signed, graded = graded,
        addedAt = 0, updatedAt = 0,
    )

    @Test fun paidVsWorthCountsOnlyCopiesWithAPricePaid() {
        val s = StatsMath.compute(
            listOf(
                card("a", qty = 2, price = 5.0, paid = 3.0),
                card("b", price = 10.0),
                card("c", price = 1.0, paid = 4.0),
                card("proxy", price = 50.0, paid = 1.0, proxy = true),
            ),
        )
        assertEquals(3, s.paid.copies)
        assertEquals(10.0, s.paid.paid, 0.001)
        assertEquals(11.0, s.paid.worth, 0.001)
        assertEquals(1.0, s.paid.change, 0.001)
    }

    @Test fun paidIsEmptyWithoutPrices() {
        val s = StatsMath.compute(listOf(card("a"), card("b")))
        assertEquals(0, s.paid.copies)
        assertEquals(0.0, s.paid.paid, 0.001)
    }

    @Test fun tileFlagsMatchWhatTheTilesCount() {
        val cards = listOf(
            card("cmdr", type = "Legendary Creature — Elf", rarity = "rare", price = 3.0),
            card("pw", type = "Legendary Planeswalker", text = "Teferi can be your commander.", rarity = "mythic", price = 9.0),
            card("bulk", qty = 3, price = 0.1),
            card("sale", sale = true, rarity = "rare", price = 2.0),
            card("prox", proxy = true, rarity = "rare"),
            card("sig", signed = true, rarity = "rare"),
            card("psa", graded = "PSA 9", rarity = "rare"),
        )
        val counts = TileMath.counts(cards, boxes = 1, wishlist = 0, now = 0)
        for (metric in listOf(TileMetric.COMMANDERS, TileMetric.BULK, TileMetric.FOR_SALE, TileMetric.PROXIES, TileMetric.SIGNED, TileMetric.GRADED)) {
            val flag = TileFlags.of(metric)!!
            val shown = cards.filter { TileFlags.matches(it, flag) }.sumOf { it.quantity }.toDouble()
            assertEquals(metric.name, counts[metric]!!, shown, 0.001)
        }
    }

    @Test fun onlyFilterTilesHaveFlags() {
        assertNull(TileFlags.of(TileMetric.TOTAL))
        assertNull(TileFlags.of(TileMetric.WEEK))
        assertTrue(TileFlags.matches(card("x"), "unknown-flag"))
        assertFalse(TileFlags.matches(card("x"), TileFlags.SIGNED))
    }
}
