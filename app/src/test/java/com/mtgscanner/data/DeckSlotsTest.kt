package com.mtgscanner.data

import com.mtgscanner.data.db.Box
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.deckPlaceName
import com.mtgscanner.data.db.facts
import com.mtgscanner.data.db.isDeckSlot
import com.mtgscanner.data.db.placeName
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cards kept in a deck (a boxes row with a deck id) are a place of their own, outside every box. */
class DeckSlotsTest {
    private val red = Box(id = 1, name = "Red", rules = "", sortOrder = 0)
    private val anything = Box(id = 2, name = "Anything", sortOrder = 1)
    /** An old deck box: still a box. */
    private val oldDeckBox = Box(id = 3, name = "Burn", kind = "deck", sortOrder = 2)
    private val atraxa = Box(id = 4, name = "Atraxa", kind = "deck", deckId = 9)

    private var nextId = 1L
    private fun card(name: String, box: Long, colors: String = "R", qty: Int = 1) = CollectionCard(
        id = nextId++, scryfallId = "$name-id", oracleId = "$name-o", name = name, setCode = "M10", setName = "M10",
        collectorNumber = "1", rarity = "common", typeLine = "Instant", manaCost = "", cmc = 1.0, colors = colors,
        colorIdentity = colors, oracleText = "", tags = "", priceUsd = 1.0, priceUsdFoil = null, imageUrl = null,
        smallImageUrl = null, scryfallUri = "", lang = "en", releasedAt = null, artist = null, foil = false, quantity = qty,
        boxId = box, addedAt = 0, updatedAt = 0,
    )

    @Test fun deckRowsReadAsDecks() {
        assertTrue(atraxa.isDeckSlot)
        assertFalse(oldDeckBox.isDeckSlot)
        assertEquals("Deck: Atraxa", atraxa.placeName())
        assertEquals("Burn", oldDeckBox.placeName())
        assertEquals("Deck: Atraxa", deckPlaceName("Atraxa"))
    }

    @Test fun whereIsNamesTheDeck() {
        val bolt = card("Lightning Bolt", atraxa.id)
        val wing = card("Boltwing Marauder", red.id)
        assertEquals("Deck: Atraxa", Placement.whereIs(bolt, atraxa, listOf(bolt)))
        val found = Placement.find("bolt", listOf(bolt, wing), listOf(red, anything, atraxa))
        assertEquals(listOf("Red", "Deck: Atraxa"), found.map { it.boxName })
        assertEquals("Deck: Atraxa", found.last().where)
    }

    @Test fun sortingNeverFlagsCardsInDecks() {
        // Boxes as the sorting code sees them: the deck's row isn't one.
        val boxes = listOf(
            BoxInfo(red.id, BoxRules(colors = setOf("R"))),
            BoxInfo(anything.id, BoxRules()),
        )
        val inDeck = card("Lightning Bolt", atraxa.id)
        val misfiled = card("Opt", red.id, colors = "U")
        val moves = Sorting.misplaced(listOf(inDeck, misfiled), { it.boxId }, { it.facts() }, boxes, copies = { it.quantity })
        assertEquals(listOf(misfiled.id), moves.map { it.item.id })
    }

    @Test fun boxesSavedBeforeDecksHeldCardsReadAsBoxes() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val old = json.decodeFromString(Box.serializer(), """{"id":3,"name":"Burn","kind":"deck","createdAt":5}""")
        assertEquals(0L, old.deckId)
        assertFalse(old.isDeckSlot)
        val back = json.decodeFromString(Box.serializer(), json.encodeToString(Box.serializer(), atraxa))
        assertEquals(atraxa, back)
    }
}
