package com.mtgscanner.ui.sell

import com.mtgscanner.data.db.CollectionCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BuylistMathTest {
    private fun card(id: Long, box: Long = 1, qty: Int = 1, slot: Int = 0, proxy: Boolean = false, name: String = "c$id") = CollectionCard(
        id = id, scryfallId = "s$id", oracleId = "o$id", name = name, setCode = "m10", setName = "M10",
        collectorNumber = "1", rarity = "rare", typeLine = "Instant", manaCost = "", cmc = 1.0, colors = "R",
        colorIdentity = "R", oracleText = "", tags = "", priceUsd = 2.0, priceUsdFoil = null, imageUrl = null,
        smallImageUrl = null, scryfallUri = "", lang = "en", releasedAt = null, artist = null, foil = false, quantity = qty,
        boxId = box, binderSlot = slot, proxy = proxy, addedAt = 0, updatedAt = 0,
    )

    @Test fun onlyCardsAtOrAboveTheMinimumInBoxOrder() {
        val cards = listOf(card(1, box = 2, name = "b"), card(2, box = 1, name = "z"), card(3, box = 1, name = "a"), card(4, box = 1), card(5, proxy = true))
        val prices = mapOf(1L to 3.0, 2L to 1.0, 3L to 5.0, 4L to 0.5, 5L to 9.0)
        val list = BuylistMath.pullList(cards, 1.0, listOf(1, 2), { prices[it.id] })
        assertEquals(listOf(3L, 2L, 1L), list.map { it.card.id })
    }

    @Test fun lentCopiesStay() {
        val list = BuylistMath.pullList(listOf(card(1, qty = 3), card(2)), 1.0, listOf(1), { 2.0 }, outOf = { 1 })
        assertEquals(listOf(1L), list.map { it.card.id })
        assertEquals(2, list[0].copies)
        assertEquals(4.0, list[0].total, 0.001)
    }

    @Test fun binderSlotsInOrder() {
        val list = BuylistMath.pullList(listOf(card(1, slot = 12), card(2, slot = 3), card(3)), 0.5, listOf(1), { 1.0 })
        assertEquals(listOf(2L, 1L, 3L), list.map { it.card.id })
        assertEquals("Page 2", BuylistMath.pageOf(12))
        assertNull(BuylistMath.pageOf(0))
    }
}
