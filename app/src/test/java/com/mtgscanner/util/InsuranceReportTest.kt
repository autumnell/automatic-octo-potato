package com.mtgscanner.util

import com.mtgscanner.data.db.CollectionCard
import org.junit.Assert.assertEquals
import org.junit.Test

class InsuranceReportTest {
    private fun card(name: String, usd: Double?, qty: Int = 1, proxy: Boolean = false) = CollectionCard(
        id = 1, scryfallId = "s-$name", oracleId = "o-$name", name = name, setCode = "M10", setName = "M10", collectorNumber = "1",
        rarity = "common", typeLine = "Instant", manaCost = "{R}", cmc = 1.0, colors = "R", colorIdentity = "R",
        oracleText = "", tags = "", priceUsd = usd, priceUsdFoil = null, imageUrl = null, smallImageUrl = null,
        scryfallUri = "", lang = "en", releasedAt = null, artist = null, foil = false, quantity = qty,
        boxId = 1, proxy = proxy, addedAt = 0, updatedAt = 0,
    )

    private val cards = listOf(
        card("Ten", 10.0),
        card("Five threes", 3.0, qty = 5),
        card("Bulk", 0.5, qty = 100),
        card("Proxy", 99.0, proxy = true),
        card("Unpriced", null),
    )

    /** The floor is per copy; the order is by what the row is worth. */
    @Test fun leavesOutCheapCardsAndProxiesMostValuableRowFirst() {
        assertEquals(listOf("Five threes", "Ten"), InsuranceReport.select(cards, 1.0).map { it.name })
    }

    @Test fun noFloorListsEverythingButProxies() {
        assertEquals(listOf("Bulk", "Five threes", "Ten", "Unpriced"), InsuranceReport.select(cards, 0.0).map { it.name })
    }
}
