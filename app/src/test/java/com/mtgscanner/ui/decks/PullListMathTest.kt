package com.mtgscanner.ui.decks

import com.mtgscanner.data.db.Box
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.util.DeckLine
import org.junit.Assert.assertEquals
import org.junit.Test

class PullListMathTest {
    private var nextId = 1L

    private fun card(
        name: String, box: Long, qty: Int = 1, set: String = "M10", num: String = "1",
        price: Double? = 1.0, foil: Boolean = false, proxy: Boolean = false,
    ) = CollectionCard(
        id = nextId++, scryfallId = "$name-$set-$num", oracleId = null, name = name, setCode = set, setName = set,
        collectorNumber = num, rarity = "common", typeLine = "Instant", manaCost = "", cmc = 1.0, colors = "R",
        colorIdentity = "R", oracleText = "", tags = "", priceUsd = price, priceUsdFoil = price, imageUrl = null,
        smallImageUrl = null, scryfallUri = "", lang = "en", releasedAt = null, artist = null, foil = foil, quantity = qty,
        boxId = box, proxy = proxy, addedAt = 0, updatedAt = 0,
    )

    private val boxes = listOf(
        Box(id = 2, name = "Binder", sortOrder = 0),
        Box(id = 1, name = "Bulk", sortOrder = 1),
        Box(id = 3, name = "Burn", kind = "deck", sortOrder = 2),
    )

    @Test
    fun prefersRealCheapNonFoilAndSortsByBox() {
        val cards = listOf(
            card("Lightning Bolt", 1, qty = 2, price = 0.5, proxy = true),
            card("Lightning Bolt", 1, qty = 1, price = 3.0, foil = true),
            card("Lightning Bolt", 2, qty = 2, price = 2.0, set = "2XM", num = "129"),
            card("Lightning Bolt", 1, qty = 1, price = 1.0, set = "A25", num = "141"),
            card("Duress", 1, qty = 1),
        )
        val lines = listOf(DeckLine(4, "Lightning Bolt"), DeckLine(1, "Duress", section = "Sideboard"))
        val p = PullListMath.build(lines, cards, boxes, "Other deck")
        assertEquals(listOf("Binder", "Bulk"), p.groups.map { it.boxName })
        assertEquals(2, p.groups[0].items.single().qty)
        // A25 (cheapest real non-foil) and the foil before the proxies.
        val bulk = p.groups[1].items
        assertEquals(setOf("A25", "M10"), bulk.map { it.setCode }.toSet())
        assertEquals(true, bulk.first { it.setCode == "M10" }.foil)
        assertEquals(4, p.toPull)
        assertEquals(0, p.missing.size)
    }

    @Test
    fun namedPrintingDeckBoxAndMissing() {
        val cards = listOf(
            card("Lightning Bolt", 1, qty = 1, price = 0.5),
            card("Lightning Bolt", 2, qty = 1, price = 9.0, set = "2XM", num = "129"),
            card("Shock", 3, qty = 2),
        )
        val lines = listOf(
            DeckLine(1, "Lightning Bolt", setCode = "2XM", collectorNumber = "129"),
            DeckLine(1, "Lightning Bolt"),
            DeckLine(3, "Shock"),
            DeckLine(2, "Fireblast", section = "Commander"),
        )
        val p = PullListMath.build(lines, cards, boxes, "burn")
        assertEquals("2XM", p.groups.first { it.boxName == "Binder" }.items.single().setCode)
        assertEquals("M10", p.groups.first { it.boxName == "Bulk" }.items.single().setCode)
        assertEquals(2, p.inDeckBox)
        assertEquals(listOf(PullMissing("Fireblast", 2), PullMissing("Shock", 1)), p.missing)
    }
}
