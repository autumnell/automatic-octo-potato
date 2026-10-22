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

    @Test
    fun movesForCheckedItemsCarryRowsAndCopies() {
        val bolt = card("Lightning Bolt", 1, qty = 3)
        val shock = card("Shock", 2, qty = 1)
        val p = PullListMath.build(listOf(DeckLine(2, "Lightning Bolt"), DeckLine(1, "Shock")), listOf(bolt, shock), boxes, "Other")
        val boltKey = PullListMath.itemKey(bolt)
        assertEquals(listOf(bolt.id to 2), p.movesFor(setOf(boltKey)))
        assertEquals(2, p.movesFor(setOf(boltKey, PullListMath.itemKey(shock))).size)
        // Items already in the deck box aren't moved again.
        assertEquals(emptyList<Pair<Long, Int>>(), p.movesFor(setOf(boltKey), deckBoxId = 1))
        assertEquals(emptyList<Pair<Long, Int>>(), p.movesFor(emptySet()))
    }

    // Decks hold cards: this deck's row (id 9) and another deck's (id 10) come after the boxes.
    private val places = boxes + listOf(
        Box(id = 9, name = "Burn", kind = "deck", deckId = 70),
        Box(id = 10, name = "Mono Red", kind = "deck", deckId = 71),
    )

    @Test
    fun copiesKeptInTheDeckAreInPlaceAndOtherDecksGoLast() {
        val cards = listOf(
            card("Lightning Bolt", 9, qty = 2),
            card("Lightning Bolt", 10, qty = 1, price = 0.1),
            card("Lightning Bolt", 1, qty = 1, price = 5.0),
            card("Shock", 10, qty = 1),
        )
        val lines = listOf(DeckLine(4, "Lightning Bolt"), DeckLine(1, "Shock"))
        val p = PullListMath.build(lines, cards, places, "Burn deck", deckSlotId = 9)
        assertEquals(2, p.inDeckBox)
        // Boxes first, even when another deck's copy is cheaper; that deck is named as one.
        assertEquals(listOf("Bulk", "Deck: Mono Red"), p.groups.map { it.boxName })
        assertEquals(1, p.groups[0].items.single().qty)
        assertEquals(2, p.groups[1].items.sumOf { it.qty })
        assertEquals(3, p.toPull)
        // Without the deck's own row (a deck that never held cards) nothing is in place.
        assertEquals(0, PullListMath.build(lines, cards, places, "Burn deck").inDeckBox)
    }

    @Test
    fun commanderComesFirst() {
        val atraxa = card("Atraxa, Praetors' Voice", 2)
        val sol = card("Sol Ring", 1)
        val lines = listOf(
            DeckLine(1, "Sol Ring"), DeckLine(1, "Atraxa, Praetors' Voice", section = "Commander"),
            DeckLine(1, "Yorion, Sky Nomad", section = "Companion"), DeckLine(1, "Arcane Signet"),
        )
        val p = PullListMath.build(lines, listOf(atraxa, sol), boxes, "Atraxa")
        val (leads, rest) = PullListMath.splitLeads(p, listOf("Atraxa, Praetors' Voice", "Yorion, Sky Nomad"), DeckNames::key)
        assertEquals(listOf("Binder" to "Atraxa, Praetors' Voice"), leads.items.map { (box, it) -> box to it.name })
        assertEquals(listOf(PullMissing("Yorion, Sky Nomad", 1)), leads.missing)
        // The Binder group held only the commander, so it's gone from the rest.
        assertEquals(listOf("Bulk"), rest.groups.map { it.boxName })
        assertEquals(listOf("Arcane Signet"), rest.missing.map { it.name })
        // No leads: nothing moves.
        assertEquals(p, PullListMath.splitLeads(p, emptyList(), DeckNames::key).second)
    }

    @Test
    fun leadsFirstKeepsLeadOrder() {
        val names = listOf("Sol Ring", "Tymna the Weaver", "Arcane Signet", "Thrasios, Triton Hero")
        val (leads, rest) = PullListMath.leadsFirst(names, listOf("Thrasios, Triton Hero", "Tymna the Weaver"), { it }, DeckNames::key)
        assertEquals(listOf("Thrasios, Triton Hero", "Tymna the Weaver"), leads)
        assertEquals(listOf("Sol Ring", "Arcane Signet"), rest)
        assertEquals(emptyList<String>() to names, PullListMath.leadsFirst(names, emptyList(), { it }, DeckNames::key))
    }
}
