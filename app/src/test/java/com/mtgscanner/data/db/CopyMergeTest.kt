package com.mtgscanner.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CopyMergeTest {
    private fun row(qty: Int, priceAtAdd: Double? = null, paid: Double? = null, condition: String = "NM", lang: String = "en") = CollectionCard(
        id = 1, scryfallId = "s", oracleId = "o", name = "Bolt", setCode = "M10", setName = "M10", collectorNumber = "1",
        rarity = "common", typeLine = "Instant", manaCost = "{R}", cmc = 1.0, colors = "R", colorIdentity = "R",
        oracleText = "", tags = "", priceUsd = 1.0, priceUsdFoil = null, imageUrl = null, smallImageUrl = null,
        scryfallUri = "", lang = lang, releasedAt = null, artist = null, foil = false, quantity = qty, condition = condition,
        boxId = 1, priceAtAdd = priceAtAdd, purchasePrice = paid, addedAt = 0, updatedAt = 0,
    )

    @Test fun pricesAreWeightedByCopies() {
        val m = row(3, priceAtAdd = 1.0).mergedWith(row(1, priceAtAdd = 5.0), now = 9)
        assertEquals(4, m.quantity)
        assertEquals(2.0, m.priceAtAdd!!, 1e-9)
        assertNull(m.purchasePrice)
        assertEquals(9L, m.updatedAt)
    }

    @Test fun unknownPriceDoesNotDragTheAverage() {
        assertEquals(5.0, blendPrice(null, 3, 5.0, 1)!!, 1e-9)
        assertEquals(1.0, blendPrice(1.0, 3, null, 1)!!, 1e-9)
        assertNull(blendPrice(null, 1, null, 1))
    }

    @Test fun paidFallsBackToPriceAtAddForTheOtherCopies() {
        val m = row(1, priceAtAdd = 2.0).mergedWith(row(1, priceAtAdd = 3.0, paid = 4.0), copies = 1)
        assertEquals(3.0, m.purchasePrice!!, 1e-9)
    }

    @Test fun detailsFillOnlyWhenEmpty() {
        val a = row(1).copy(notes = "mine", userTags = "deck")
        val b = row(1).copy(notes = "theirs", userTags = "Deck,lent", photo = "p.jpg")
        val m = a.mergedWith(b)
        assertEquals("mine", m.notes)
        assertEquals("deck,lent", m.userTags)
        assertEquals("p.jpg", m.photo)
        assertEquals("", a.mergedWith(b, details = false).photo)
    }

    @Test fun copiesThatDifferAreNotTheSame() {
        assertTrue(row(1).sameCopyAs(row(4)))
        assertFalse(row(1).sameCopyAs(row(1, condition = "LP")))
        assertFalse(row(1).sameCopyAs(row(1, lang = "ja")))
        assertFalse(row(1).sameCopyAs(row(1).copy(signed = true)))
    }
}
