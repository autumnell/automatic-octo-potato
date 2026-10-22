package com.mtgscanner.data.sync

import com.mtgscanner.data.db.CollectionCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncFieldsTest {
    private val card = CollectionCard(
        id = 42, scryfallId = "s1", oracleId = "o1", name = "Opt", setCode = "dom", setName = "Dominaria",
        collectorNumber = "60", rarity = "common", typeLine = "Instant", manaCost = "{U}", cmc = 1.0,
        colors = "U", colorIdentity = "U", oracleText = "Scry 1.", tags = "", priceUsd = 0.1, priceUsdFoil = 0.5,
        imageUrl = "https://img/normal/x.jpg", smallImageUrl = null, scryfallUri = "https://scryfall.com/x", lang = "en",
        releasedAt = "2018-04-27", artist = null, foil = true, quantity = 3, condition = "LP", notes = "hi",
        boxId = 7, purchasePrice = 0.25, photo = "/data/p.jpg", userTags = "lent", forSale = true, binderSlot = 4,
        addedAt = 1000, updatedAt = 2000,
    )

    @Test fun `fields round trip`() {
        val f = SyncFields.of(card, "Blue binder")
        assertEquals("Blue binder", f["box"])
        val blank = card.copy(
            quantity = 1, foil = false, condition = "NM", notes = "", userTags = "", purchasePrice = null,
            forSale = false, binderSlot = 0,
        )
        assertEquals(card, SyncFields.apply(blank, f))
    }

    @Test fun `null prices round trip`() {
        val c = card.copy(purchasePrice = null, askPrice = null)
        val f = SyncFields.of(c, "x")
        assertEquals("", f["paid"])
        assertEquals(c, SyncFields.apply(card, f))
    }

    @Test fun `bad values are ignored or made safe`() {
        val r = SyncFields.apply(card, mapOf("qty" to "0", "slot" to "abc", "cond" to ""))
        assertEquals(1, r.quantity)
        assertEquals(4, r.binderSlot)
        assertEquals("LP", r.condition)
    }

    @Test fun `base leaves out local bits`() {
        val back = SyncFields.fromBase(SyncFields.base(card))!!
        assertEquals(0L, back.id)
        assertEquals("", back.photo)
        assertEquals(0L, back.boxId)
        assertEquals("Opt", back.name)
        assertEquals(1000L, back.addedAt)
        assertNull(SyncFields.fromBase("not json"))
    }

    @Test fun `id map keeps ids and guards reused rows`() {
        var n = 0
        val m1 = IdMap().assign(listOf(1L to "s1", 2L to "s2")) { "id${++n}" }
        assertEquals("id1", m1.idOf(1, "s1"))
        // Row 2 now holds another card: it gets a new id. Row 1 is gone.
        val m2 = m1.assign(listOf(2L to "s9", 3L to "s3")) { "id${++n}" }
        assertNull(m2.idOf(1, "s1"))
        assertEquals("id3", m2.idOf(2, "s9"))
        assertEquals("id4", m2.idOf(3, "s3"))
        assertEquals(3L, m2.rowOf("id4"))
        assertNull(m2.without(3).rowOf("id4"))
        assertEquals("z", m2.with(9, IdEntry("z", "s")).idOf(9, "s"))
    }
}
