package com.mtgscanner.data

import com.mtgscanner.data.db.CollectionCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A box's value, its change since the cards were added, and the card shown behind it. */
class BoxArtTest {
    private fun card(
        id: Long, usd: Double?, qty: Int = 1, image: String? = null, box: Long = 1,
        priceAtAdd: Double? = null, paid: Double? = null, proxy: Boolean = false,
    ) = CollectionCard(
        id = id, scryfallId = "s$id", oracleId = "o$id", name = "Card $id", setCode = "M10", setName = "M10", collectorNumber = "1",
        rarity = "common", typeLine = "Instant", manaCost = "{R}", cmc = 1.0, colors = "R", colorIdentity = "R",
        oracleText = "", tags = "", priceUsd = usd, priceUsdFoil = null, imageUrl = image, smallImageUrl = null,
        scryfallUri = "", lang = "en", releasedAt = null, artist = null, foil = false, quantity = qty,
        boxId = box, priceAtAdd = priceAtAdd, purchasePrice = paid, proxy = proxy, addedAt = 0, updatedAt = 0,
    )

    private val a = card(1, 2.0, qty = 3, image = "https://cards.scryfall.io/normal/front/a.jpg", priceAtAdd = 1.0)
    private val b = card(2, 10.0, priceAtAdd = 8.0, paid = 4.0)
    private val c = card(3, 5.0, image = "https://cards.scryfall.io/large/front/c.jpg")
    private val proxy = card(4, 50.0, priceAtAdd = 50.0, proxy = true)
    private val box = listOf(a, b, c, proxy)

    @Test fun thePickedCoverWinsWhileItHasAPicture() {
        assertEquals(1L, BoxArt.coverCard(box, 1)?.id)
        // No pick, a pick with no picture, or a pick that left the box: the most valuable card with a picture.
        assertEquals(3L, BoxArt.coverCard(box, null)?.id)
        assertEquals(3L, BoxArt.coverCard(box, 2)?.id)
        assertEquals(3L, BoxArt.coverCard(box, 99)?.id)
        assertNull(BoxArt.coverCard(listOf(b, proxy), null))
        assertNull(BoxArt.coverCard(emptyList(), 1))
    }

    @Test fun changeComparesOnlyCardsWithAPriceFromWhenAdded() {
        val s = BoxArt.statsFor(box, 1)
        // 3 x 2.00 + 10.00 + 5.00; the proxy counts nothing.
        assertEquals(21.0, s.value, 1e-9)
        // a at 1.00 each and b at the 4.00 paid; c has no price from then and the proxy is left out.
        assertEquals(7.0, s.valueThen, 1e-9)
        assertEquals(16.0, s.valueNowTracked, 1e-9)
        assertEquals(9.0, s.change, 1e-9)
        assertEquals(900.0 / 7.0, s.changePercent!!, 1e-9)
        assertEquals(1L, s.coverCardId)
        assertEquals("https://cards.scryfall.io/art_crop/front/a.jpg", s.artUrl)
    }

    @Test fun nothingTrackedMeansNoPercent() {
        val s = BoxArt.statsFor(listOf(c), null)
        assertEquals(5.0, s.value, 1e-9)
        assertEquals(0.0, s.change, 0.0)
        assertNull(s.changePercent)
        assertNull(BoxStats().changePercent)
    }

    @Test fun statsAreKeptPerBox() {
        val other = card(5, 4.0, qty = 2, box = 2, image = "https://cards.scryfall.io/small/front/e.jpg")
        val stats = BoxArt.stats(box + other, mapOf(1L to 3L, 2L to 5L))
        assertEquals(setOf(1L, 2L), stats.keys)
        assertEquals(3L, stats.getValue(1).coverCardId)
        assertEquals(8.0, stats.getValue(2).value, 1e-9)
        assertEquals("https://cards.scryfall.io/art_crop/front/e.jpg", stats.getValue(2).artUrl)
    }
}
