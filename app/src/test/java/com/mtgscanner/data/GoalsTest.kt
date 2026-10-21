package com.mtgscanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoalsTest {
    private fun card(id: String, oracle: String?, usd: String? = null, foil: String? = null, etched: String? = null) = ScryfallCard(
        id = id, name = "Card $id", oracleId = oracle, set = "m10", setName = "Magic 2010", collectorNumber = id, rarity = "rare",
        prices = Prices(usd = usd, usdFoil = foil, usdEtched = etched),
    )

    @Test
    fun cheapestFinish() {
        assertEquals(1.5, Goals.cheapest(card("1", "a", usd = "2.00", foil = "1.50"))!!, 1e-9)
        assertEquals(4.0, Goals.cheapest(card("2", "a", foil = "4"))!!, 1e-9)
        assertEquals(9.0, Goals.cheapest(card("3", "a", etched = "9"))!!, 1e-9)
        assertNull(Goals.cheapest(card("4", "a")))
    }

    @Test
    fun costSkipsUnpriced() {
        val missing = listOf(card("1", "a", usd = "2.25"), card("2", "b"), card("3", "c", foil = "0.75"))
        assertEquals(3.0, Goals.cost(missing), 1e-9)
    }

    @Test
    fun wishlistOncePerCardSkippingWished() {
        val missing = listOf(card("1", "a"), card("2", "a"), card("3", "b"), card("4", null), card("5", "c"))
        assertEquals(listOf("1", "5"), Goals.toWishlist(missing, wished = setOf("b")).map { it.id })
    }
}
