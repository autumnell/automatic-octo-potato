package com.mtgscanner.ui.home

import com.mtgscanner.data.db.CollectionCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsMathTest {
    private fun card(
        id: String, qty: Int = 1, price: Double? = 1.0, colors: String = "R", rarity: String = "common",
        set: String = "m10", proxy: Boolean = false, foil: Boolean = false, foilPrice: Double? = null,
        type: String = "Instant", added: Long = 0,
    ) = CollectionCard(
        scryfallId = id, oracleId = id, name = id, setCode = set, setName = "Set $set",
        collectorNumber = "1", rarity = rarity, typeLine = type, manaCost = "", cmc = 1.0, colors = colors,
        colorIdentity = colors, oracleText = "", tags = "", priceUsd = price, priceUsdFoil = foilPrice, imageUrl = null,
        smallImageUrl = null, scryfallUri = "", lang = "en", releasedAt = null, artist = null, foil = foil, quantity = qty,
        boxId = 1, proxy = proxy, addedAt = added, updatedAt = 0,
    )

    @Test fun valueBySet() {
        val s = StatsMath.compute(listOf(card("a", qty = 2, price = 3.0, set = "a"), card("b", price = 10.0, set = "b"), card("c", price = 1.0, set = "a")))
        assertEquals(listOf("b", "a"), s.bySet.map { it.key })
        assertEquals(7.0, s.bySet[1].value, 0.001)
        assertEquals(3, s.bySet[1].cards)
        assertEquals(17.0, s.value, 0.001)
        assertEquals(4, s.cards)
    }

    @Test fun multicolorCountsInEachColorAndNoMulti() {
        val s = StatsMath.compute(listOf(card("wu", qty = 2, price = 5.0, colors = "WU"), card("art", price = 2.0, colors = "")))
        val by = s.byColor.associateBy { it.key }
        assertEquals(listOf("W", "U", "B", "R", "G", "C"), s.byColor.map { it.key })
        assertEquals(2, by["W"]!!.cards)
        assertEquals(10.0, by["W"]!!.value, 0.001)
        assertEquals(10.0, by["U"]!!.value, 0.001)
        assertEquals(0, by["R"]!!.cards)
        assertEquals(2.0, by["C"]!!.value, 0.001)
    }

    @Test fun rarityOrderAndSpecial() {
        val s = StatsMath.compute(listOf(card("m", rarity = "mythic", price = 20.0), card("c"), card("s", rarity = "bonus", price = 4.0)))
        assertEquals(listOf("common", "mythic", "special"), s.byRarity.map { it.key })
        assertEquals(20.0, s.byRarity[1].value, 0.001)
    }

    @Test fun proxiesAreWorthNothingAndLeftOutOfTop() {
        val s = StatsMath.compute(listOf(card("p", price = 100.0, proxy = true), card("a", price = 2.0)))
        assertEquals(2.0, s.value, 0.001)
        assertEquals(listOf("a"), s.top.map { it.name })
        assertEquals(2, s.cards)
    }

    @Test fun topIsByPriceEachFoilAware() {
        val cards = (1..25).map { card("c$it", price = it.toDouble()) } + card("f", price = 1.0, foil = true, foilPrice = 50.0)
        val top = StatsMath.compute(cards).top
        assertEquals(20, top.size)
        assertEquals("f", top[0].name)
        assertEquals("c25", top[1].name)
    }

    @Test fun tileCounts() {
        val now = 10L * 24 * 60 * 60 * 1000
        val cards = listOf(
            card("a", qty = 3, rarity = "rare", type = "Legendary Creature — Elf", added = now - 1000),
            card("b", qty = 2, foil = true, foilPrice = 4.0, type = "Basic Land — Forest"),
            card("c", qty = 1, price = 6.0, proxy = true, rarity = "mythic"),
        )
        val m = TileMath.counts(cards, boxes = 2, wishlist = 5, now = now)
        assertEquals(6.0, m[TileMetric.TOTAL]!!, 0.0)
        assertEquals(3.0, m[TileMetric.UNIQUE]!!, 0.0)
        assertEquals(2.0, m[TileMetric.FOILS]!!, 0.0)
        assertEquals(3.0, m[TileMetric.RARES]!!, 0.0)
        assertEquals(1.0, m[TileMetric.MYTHICS]!!, 0.0)
        assertEquals(2.0, m[TileMetric.LANDS]!!, 0.0)
        assertEquals(3.0, m[TileMetric.COMMANDERS]!!, 0.0)
        assertEquals(3.0, m[TileMetric.WEEK]!!, 0.0)
        assertEquals(1.0, m[TileMetric.PROXIES]!!, 0.0)
        assertEquals(5.0, m[TileMetric.WISHLIST]!!, 0.0)
        // 3×$1 + 2×$4 = $11 over 5 real copies.
        assertEquals(11.0, m[TileMetric.VALUE]!!, 0.001)
        assertEquals(2.2, m[TileMetric.AVERAGE]!!, 0.001)
        assertTrue(TileMetric.entries.all { it == TileMetric.CUSTOM || it in m })
    }

    @Test fun euroValues() {
        val cards = listOf(
            card("a", qty = 2).copy(priceEur = 1.5),
            card("b", qty = 1, set = "m11").copy(priceEur = 4.0),
            card("c", qty = 1, proxy = true).copy(priceEur = 9.0),
        )
        val eur = TileMath.eurCounts(cards)
        // 2×€1.50 + €4 = €7 over 3 real copies; the proxy is worth nothing.
        assertEquals(7.0, eur[TileMetric.VALUE]!!, 0.001)
        assertEquals(7.0 / 3, eur[TileMetric.AVERAGE]!!, 0.001)
        val s = StatsMath.compute(cards)
        assertEquals(7.0, s.valueEur, 0.001)
        assertEquals(4.0, s.bySet.first { it.key == "m11" }.eur, 0.001)
        assertEquals(7.0, s.byColor.first { it.key == "R" }.eur, 0.001)
        assertEquals("€1.2k", TileMath.text(TileMetric.VALUE, 1234.0, euro = true))
        assertEquals("$1.2k", TileMath.text(TileMetric.VALUE, 1234.0))
    }

    @Test fun shortNumbers() {
        assertEquals("$3.40", TileMath.shortUsd(3.4))
        assertEquals("$150", TileMath.shortUsd(150.2))
        assertEquals("$1.2k", TileMath.shortUsd(1234.0))
        assertEquals("$12k", TileMath.shortUsd(12_345.0))
        assertEquals("999", TileMath.shortCount(999.0))
        assertEquals("12.3k", TileMath.shortCount(12_345.0))
    }

    @Test fun bulkCountsCheapCommonsAndUncommons() {
        val s = StatsMath.compute(
            listOf(
                card("a", qty = 3, price = 0.10), card("b", qty = 2, price = 0.20, rarity = "uncommon"),
                card("c", price = 2.0), card("d", price = 0.10, rarity = "rare"), card("e", price = 0.10, proxy = true),
            ),
        )
        assertEquals(5, s.bulkCards)
    }
}
