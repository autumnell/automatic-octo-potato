package com.mtgscanner.ui.detail

import com.mtgscanner.data.db.CollectionCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitMathTest {
    private fun row(qty: Int = 4, condition: String = "NM", foil: Boolean = false, lang: String = "en") = CollectionCard(
        id = 7, scryfallId = "s", oracleId = "o", name = "Bolt", setCode = "m10", setName = "M10",
        collectorNumber = "1", rarity = "common", typeLine = "Instant", manaCost = "{R}", cmc = 1.0, colors = "R",
        colorIdentity = "R", oracleText = "", tags = "", priceUsd = 1.0, priceUsdFoil = 3.0, imageUrl = null,
        smallImageUrl = null, scryfallUri = "", lang = lang, releasedAt = null, artist = null, foil = foil, quantity = qty,
        condition = condition, boxId = 1, conditionPrice = 0.8, conditionPriceAt = 5, binderSlot = 3, photo = "/p.jpg",
        notes = "front row", addedAt = 1, updatedAt = 1,
    )

    @Test fun needsAChangeAndCopiesLeftBehind() {
        assertTrue(SplitMath.valid(row(), SplitChange(1, "LP", false, "en")))
        assertTrue(SplitMath.valid(row(), SplitChange(3, "NM", true, "en")))
        assertTrue(SplitMath.valid(row(), SplitChange(2, "NM", false, "ja")))
        assertFalse(SplitMath.valid(row(), SplitChange(1, "NM", false, "EN")))
        assertFalse(SplitMath.valid(row(), SplitChange(4, "LP", false, "en")))
        assertFalse(SplitMath.valid(row(), SplitChange(0, "LP", false, "en")))
        assertFalse(SplitMath.valid(row(qty = 1), SplitChange(1, "LP", false, "en")))
    }

    @Test fun splitRowIsANewRowWithItsOwnPriceAndNoPocketOrPhoto() {
        val r = SplitMath.splitRow(row(), SplitChange(1, "LP", true, "de"), now = 99)
        assertEquals(0L, r.id)
        assertEquals(1, r.quantity)
        assertEquals("LP", r.condition)
        assertTrue(r.foil)
        assertEquals("de", r.lang)
        assertEquals(null, r.conditionPrice)
        assertEquals(0L, r.conditionPriceAt)
        assertEquals(0, r.binderSlot)
        assertEquals("", r.photo)
        assertEquals("front row", r.notes)
        assertEquals(1L, r.addedAt)
        assertEquals(99L, r.updatedAt)
    }

    @Test fun messageSaysWhatChanged() {
        assertEquals("1 of 4 is LP", SplitMath.message(row(), SplitChange(1, "LP", false, "en")))
        assertEquals("2 of 4 are LP, foil", SplitMath.message(row(), SplitChange(2, "LP", true, "en")))
        assertEquals("1 of 4 is non-foil, JA", SplitMath.message(row(foil = true), SplitChange(1, "NM", false, "ja")))
    }
}
