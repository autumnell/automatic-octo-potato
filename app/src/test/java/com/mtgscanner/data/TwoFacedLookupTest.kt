package com.mtgscanner.data

import com.mtgscanner.data.PrintingLookup.Ident
import com.mtgscanner.data.PrintingLookup.Wanted
import com.mtgscanner.util.CardNames
import com.mtgscanner.util.DecklistParser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Two-faced cards through the deck path: link import, lookup, owned matching, "Add to collection". */
class TwoFacedLookupTest {
    private data class Card(val name: String, val set: String, val number: String)

    /** Answers like /cards/collection where a full "A // B" name isn't found for adventures and DFCs, the front face is. */
    private class FakeScryfall(val cards: List<Card>) {
        val sent = mutableListOf<Ident>()
        fun lookup(ids: List<Ident>): List<Card?> = ids.map { i ->
            sent += i
            when {
                i.set != null && i.number != null -> cards.firstOrNull { it.set == i.set && it.number == i.number }
                else -> cards.firstOrNull { c ->
                    val nameOk = if (c.name.startsWith("Fire")) c.name.equals(i.name, true) || c.name.substringBefore(" //").equals(i.name, true)
                    else c.name.substringBefore(" //").equals(i.name, true)
                    nameOk && (i.set == null || c.set == i.set)
                }
            }
        }
    }

    private val scry = FakeScryfall(
        listOf(
            Card("Inverted Iceberg // Iceberg Titan", "lci", "60"),
            Card("Queen of Ice // Rage of Winter", "eld", "44"),
            Card("Fire // Ice", "mh2", "290"),
        ),
    )

    @Test fun attemptsTryFullNameThenFrontFace() {
        val a = PrintingLookup.attempts(Wanted("Queen of Ice // Rage of Winter", "eld"))
        assertEquals(
            listOf(
                Ident(set = "eld", name = "Queen of Ice // Rage of Winter"), Ident(set = "eld", name = "Queen of Ice"),
                Ident(name = "Queen of Ice // Rage of Winter"), Ident(name = "Queen of Ice"),
            ),
            a,
        )
        assertEquals(listOf(Ident(name = "Fire // Ice"), Ident(name = "Fire")), PrintingLookup.attempts(Wanted("Fire/Ice")))
        assertEquals(listOf(Ident(name = "Lightning Bolt")), PrintingLookup.attempts(Wanted("Lightning Bolt")))
    }

    @Test fun resolvesTheScreenshotDeck() {
        // The two lines from Rae's Hylda deck, as the link import wrote them (set, no number), plus other forms.
        val wants = listOf(
            Wanted("Inverted Iceberg // Iceberg Titan", "LCI"),
            Wanted("Queen of Ice // Rage of Winter", "ELD"),
            Wanted("Queen of Ice"),
            Wanted("Fire/Ice"),
            Wanted("Inverted Iceberg // Iceberg Titan", "lci", "60"),
        )
        val found = PrintingLookup.resolve(wants) { scry.lookup(it) }
        assertEquals(listOf("lci", "eld", "eld", "mh2", "lci"), found.map { it?.set })
    }

    @Test fun moxfieldTwoFacedCardsKeepTheirNumber() {
        val body = """{"name":"Hylda","format":"commander","boards":{
            "commanders":{"cards":{"a":{"quantity":1,"card":{"name":"Hylda of the Icy Crown","set":"woe","cn":"206"}}}},
            "mainboard":{"cards":{
              "b":{"quantity":1,"finish":"nonFoil","card":{"name":"Inverted Iceberg // Iceberg Titan","set":"lci","cn":"60"}},
              "c":{"quantity":1,"finish":"etched","card":{"name":"Queen of Ice // Rage of Winter","set":"eld","cn":"44"}},
              "d":{"quantity":1,"card":{"name":"Arcane Signet","set":"m3c","cn":287}}}}}}"""
        val lines = DecklistParser.parse(DeckLinks.parseMoxfield(Json.parseToJsonElement(body))!!.text)
        val iceberg = lines.first { it.name.startsWith("Inverted") }
        assertEquals("Inverted Iceberg // Iceberg Titan", iceberg.name)
        assertEquals("lci" to "60", iceberg.setCode to iceberg.collectorNumber)
        val queen = lines.first { it.name.startsWith("Queen") }
        assertEquals("44", queen.collectorNumber)
        assertTrue(queen.foil)
        assertEquals("287", lines.first { it.name == "Arcane Signet" }.collectorNumber)
    }

    @Test fun archidektTwoFacedCards() {
        val body = """{"name":"Arch","cards":[
            {"quantity":1,"categories":["Creature"],"modifier":"Etched","card":{"oracleCard":{"name":"Inverted Iceberg // Iceberg Titan"},"edition":{"editioncode":"lci"},"collectorNumber":"60"}}]}"""
        val l = DecklistParser.parse(DeckLinks.parseArchidekt(Json.parseToJsonElement(body))!!.text).single()
        assertEquals("Inverted Iceberg // Iceberg Titan", l.name)
        assertEquals("60", l.collectorNumber)
        assertTrue(l.foil)
    }

    @Test fun addToCollectionCountsAnyFormAsOwned() {
        // Owned copies are stored under Scryfall's full name; the list may use the front face, or the other way round.
        val owned = mapOf(CardNames.key("Queen of Ice // Rage of Winter") to 1, CardNames.key("Fire // Ice") to 2)
        val lines = DecklistParser.parse("1 Queen of Ice (ELD) 44\n2 Fire/Ice\n1 Inverted Iceberg // Iceberg Titan (LCI) 60")
        val rows = DeckAdd.rows(lines, owned, onlyMissing = true, key = CardNames::key)
        assertEquals(listOf("Inverted Iceberg // Iceberg Titan"), rows.map { it.name })
        assertEquals(4, DeckAdd.rows(lines, owned, onlyMissing = false, key = CardNames::key).sumOf { it.quantity })
    }

    @Test fun deckPlacesListOtherDecksShort() {
        val places = mapOf(
            1L to DeckPlaces.Place("Deck: Untitled deck", deckId = 7),
            2L to DeckPlaces.Place("Deck: Untitled deck", deckId = 8),
            3L to DeckPlaces.Place("Deck: The Dusk Rose", deckId = 9),
            4L to DeckPlaces.Place("Binder"),
        )
        // Deck 7 is the one open: its row is "In deck"; another deck with the same name is just another deck.
        val w = DeckPlaces.where(listOf(1L to 1, 2L to 1, 3L to 1, 4L to 2, 99L to 1), places, ownSlots = setOf(1L))
        assertEquals(listOf(DeckCheckLine.IN_DECK to 1, "Binder" to 2, "Unsorted" to 1), w.boxes)
        assertEquals(2, w.otherDecks)
        assertEquals("In deck ×1 · Binder ×2 · Unsorted ×1 · 2 other decks", DeckPlaces.text(w.boxes, w.otherDecks))
        assertEquals("In deck ×1 · 1 other deck", DeckPlaces.where(listOf(1L to 1, 3L to 1), places, setOf(1L)).let { DeckPlaces.text(it.boxes, it.otherDecks) })
    }

    @Test fun missingCostKnowsWhenAPriceIsMissing() {
        fun line(price: Double?, owned: Int = 0) = DeckCheckLine("X", "Main", 1, owned, emptyList(), price, null, null)
        assertTrue(DeckCheck(listOf(line(1.0), line(null))).missingCostPartial)
        assertEquals(false, DeckCheck(listOf(line(1.0), line(null, owned = 1))).missingCostPartial)
        assertEquals(0.0, DeckCheck(emptyList()).missingCost, 0.0)
    }
}
