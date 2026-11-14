package com.mtgscanner.ui.components

import com.mtgscanner.data.db.CollectionCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardTextTest {
    private fun row(tags: String) = CollectionCard(
        id = 7, scryfallId = "s", oracleId = "o", name = "Bolt", setCode = "m10", setName = "M10",
        collectorNumber = "1", rarity = "common", typeLine = "Instant", manaCost = "{R}", cmc = 1.0, colors = "R",
        colorIdentity = "R", oracleText = "", tags = tags, priceUsd = 1.0, priceUsdFoil = 3.0, imageUrl = null,
        smallImageUrl = null, scryfallUri = "", lang = "en", releasedAt = null, artist = null, foil = false, quantity = 1,
        condition = "NM", boxId = 1, conditionPrice = 0.8, conditionPriceAt = 5, binderSlot = 3, photo = "/p.jpg",
        notes = "", addedAt = 1, updatedAt = 1,
    )

    @Test fun savedLegalitiesShowTheMainFormats() {
        val legal = row("legal:modern, legal:commander, burn").textInfo().legalities
        assertEquals("legal", legal["modern"])
        assertEquals("legal", legal["commander"])
        assertEquals("not_legal", legal["standard"])
        assertEquals(5, legal.size)
    }

    /** A card saved without legalities (matched from the on-phone list) must not read as illegal everywhere. */
    @Test fun noSavedLegalitiesSaysNothing() {
        assertTrue(row("").textInfo().legalities.isEmpty())
        assertTrue(row("burn, pet card").textInfo().legalities.isEmpty())
    }
}
