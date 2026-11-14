package com.mtgscanner.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The price helpers every total in the app is built from. */
class CardMoneyTest {
    private fun row(
        usd: Double? = null, usdFoil: Double? = null, foil: Boolean = false, qty: Int = 1, proxy: Boolean = false,
        priceAtAdd: Double? = null, paid: Double? = null, eur: Double? = null, eurFoil: Double? = null,
    ) = CollectionCard(
        id = 1, scryfallId = "s", oracleId = "o", name = "Bolt", setCode = "M10", setName = "M10", collectorNumber = "1",
        rarity = "common", typeLine = "Instant", manaCost = "{R}", cmc = 1.0, colors = "R", colorIdentity = "R",
        oracleText = "", tags = "", priceUsd = usd, priceUsdFoil = usdFoil, imageUrl = null, smallImageUrl = null,
        scryfallUri = "", lang = "en", releasedAt = null, artist = null, foil = foil, quantity = qty,
        boxId = 1, priceAtAdd = priceAtAdd, purchasePrice = paid, proxy = proxy, addedAt = 0, updatedAt = 0,
        priceEur = eur, priceEurFoil = eurFoil,
    )

    @Test fun aFoilUsesTheFoilPriceAndFallsBackToTheNormalOne() {
        assertEquals(3.0, row(usd = 1.0, usdFoil = 3.0, foil = true).unitPrice()!!, 1e-9)
        assertEquals(1.0, row(usd = 1.0, foil = true).unitPrice()!!, 1e-9)
        assertEquals(1.0, row(usd = 1.0, usdFoil = 3.0).unitPrice()!!, 1e-9)
        assertNull(row(usdFoil = 3.0).unitPrice())
        assertEquals(4.0, row(eur = 2.0, eurFoil = 4.0, foil = true).unitEur()!!, 1e-9)
        assertEquals(2.0, row(eur = 2.0, foil = true).unitEur()!!, 1e-9)
        assertNull(row(eurFoil = 4.0).unitEur())
    }

    @Test fun totalsCountCopiesButNotProxiesOrUnpricedCards() {
        assertEquals(6.0, row(usd = 1.5, qty = 4).totalPrice(), 1e-9)
        assertEquals(0.0, row(usd = 1.5, qty = 4, proxy = true).totalPrice(), 0.0)
        assertEquals(0.0, row(qty = 4).totalPrice(), 0.0)
        assertEquals(5.0, row(eur = 2.5, qty = 2).totalEur(), 1e-9)
        assertEquals(0.0, row(eur = 2.5, qty = 2, proxy = true).totalEur(), 0.0)
    }

    @Test fun whatYouPaidWinsOverThePriceWhenAdded() {
        assertEquals(2.0, row(priceAtAdd = 5.0, paid = 2.0).priceWhenAdded()!!, 1e-9)
        assertEquals(5.0, row(priceAtAdd = 5.0).priceWhenAdded()!!, 1e-9)
        assertNull(row().priceWhenAdded())
    }

    @Test fun changeNeedsBothPrices() {
        assertEquals(2.0, row(usd = 3.0, priceAtAdd = 1.0).changeSinceAdded()!!, 1e-9)
        assertEquals(-1.5, row(usd = 3.0, priceAtAdd = 1.0, paid = 4.5).changeSinceAdded()!!, 1e-9)
        assertNull(row(usd = 3.0).changeSinceAdded())
        assertNull(row(priceAtAdd = 1.0).changeSinceAdded())
    }

    @Test fun artCropSwapsTheSizeInBothLinkStyles() {
        assertEquals("https://cards.scryfall.io/art_crop/front/a/b/x.jpg", artCropUrl("https://cards.scryfall.io/normal/front/a/b/x.jpg"))
        assertEquals("https://cards.scryfall.io/art_crop/front/a/b/x.jpg", artCropUrl("https://cards.scryfall.io/large/front/a/b/x.jpg"))
        assertEquals("https://cards.scryfall.io/art_crop/front/a/b/x.jpg", artCropUrl("https://cards.scryfall.io/small/front/a/b/x.jpg"))
        assertEquals(
            "https://api.scryfall.com/cards/x?format=image&version=art_crop",
            artCropUrl("https://api.scryfall.com/cards/x?format=image&version=normal"),
        )
        assertEquals("content://photo/1", artCropUrl("content://photo/1"))
        assertNull(artCropUrl(null))
    }

    @Test fun tagListsDropBlanks() {
        assertEquals(listOf("trade", "lent"), row().copy(userTags = " trade , ,lent").userTagList())
        assertEquals(emptyList<String>(), row().userTagList())
        assertEquals(listOf("legal:modern", "ramp"), row().copy(tags = "legal:modern,ramp,").tagList())
    }
}
