package com.mtgscanner.ui.home

import com.mtgscanner.data.db.CollectionCard
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class HomeSummaryTest {
    private fun card(
        id: String, qty: Int = 1, price: Double? = 1.0, added: Long = 0, atAdd: Double? = null, proxy: Boolean = false,
        foil: Boolean = false, set: String = "m10",
    ) = CollectionCard(
        scryfallId = id, oracleId = id, name = id, setCode = set, setName = "Set",
        collectorNumber = "1", rarity = "rare", typeLine = "Instant", manaCost = "", cmc = 1.0, colors = "R",
        colorIdentity = "R", oracleText = "", tags = "", priceUsd = price, priceUsdFoil = null, imageUrl = "https://x/normal/$id.jpg",
        smallImageUrl = null, scryfallUri = "", lang = "en", releasedAt = null, artist = null, foil = foil, quantity = qty,
        boxId = 1, proxy = proxy, priceAtAdd = atAdd, addedAt = added, updatedAt = 0,
    )

    @Test fun onePassMatchesTheNumbers() {
        val cards = listOf(
            card("a", qty = 2, price = 3.0, added = 5, atAdd = 1.0),
            card("b", price = 10.0, added = 9, foil = true, set = "x"),
            card("c", price = 50.0, added = 1, proxy = true),
        ) + List(15) { card("n$it", price = 0.1, added = 100L + it) }
        val s = HomeSummary.of(cards)
        assertEquals(19, s.totalCards)
        assertEquals(18, s.uniqueCards)
        assertEquals(6.0 + 10.0 + 1.5, s.totalValue, 0.001)
        assertEquals(2, s.sets)
        assertEquals(1, s.foils)
        assertEquals(12, s.recent.size)
        assertEquals("n14", s.recent.first().name)
        assertEquals(2.0, s.valueAtAdd, 0.001)
        assertEquals(6.0, s.valueNowTracked, 0.001)
        assertEquals(listOf("a"), s.gainers.map { it.name })
        assertEquals("https://x/art_crop/b.jpg", s.heroArt)
        assertEquals('R', s.colorLetter)
    }

    @Test fun valueByYearAdded() {
        fun at(y: Int) = LocalDate.of(y, 6, 1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val cards = listOf(card("a", price = 4.0, added = at(2024), atAdd = 1.0), card("b", price = 2.0, added = at(2025)), card("c", price = 1.0, added = at(2025)))
        val rows = StatsMath.byYear(cards, ZoneOffset.UTC)
        assertEquals(listOf("2025", "2024"), rows.map { it.key })
        assertEquals(3.0, rows[0].value, 0.001)
        assertEquals(mapOf("2024" to 3.0), StatsMath.yearChange(cards, ZoneOffset.UTC))
    }
}
