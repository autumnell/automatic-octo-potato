package com.mtgscanner.data

import com.mtgscanner.data.db.Box
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.Loan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NextIdeas3Test {
    private fun card(
        id: Long, name: String = "Card $id", qty: Int = 1, box: Long = 1, identity: String = "", type: String = "Instant",
        set: String = "m10", number: String = "1",
    ) = CollectionCard(
        id = id, scryfallId = "s$id", oracleId = "o$id", name = name, setCode = set, setName = "Set",
        collectorNumber = number, rarity = "common", typeLine = type, manaCost = "", cmc = 1.0, colors = identity,
        colorIdentity = identity, oracleText = "", tags = "", priceUsd = 1.0, priceUsdFoil = null, imageUrl = null,
        smallImageUrl = null, scryfallUri = "", lang = "en", releasedAt = null, artist = null, foil = false, quantity = qty,
        boxId = box, addedAt = 0, updatedAt = 0,
    )

    private fun loan(cardId: Long, qty: Int, who: String) = Loan(id = cardId * 10, cardId = cardId, scryfallId = "s$cardId", name = "x", quantity = qty, who = who)

    private val places = listOf(Box(id = 1, name = "Binder"), Box(id = 7, name = "Atraxa", kind = "deck", deckId = 70))

    @Test fun deckRowsAreTheDecksSlot() {
        val cards = listOf(card(1, box = 1), card(2, box = 7), card(3, box = 7))
        assertEquals(listOf(2L, 3L), DeckLoanMath.rowsOf(70, cards, places).map { it.id })
        assertTrue(DeckLoanMath.rowsOf(0, cards, places).isEmpty())
    }

    @Test fun lendingSkipsCopiesAlreadyOut() {
        val cards = listOf(card(2, qty = 2, box = 7), card(3, box = 7))
        val made = DeckLoanMath.loansFor(cards, listOf(loan(3, 1, "Ana")), cards, "Sam", 5)
        assertEquals(listOf(2L to 2), made.map { it.cardId to it.quantity })
        assertTrue(made.all { it.who == "Sam" })
    }

    @Test fun deckIsLentOnlyWhenEveryCopyIsOut() {
        val cards = listOf(card(2, qty = 2, box = 7), card(3, box = 7))
        assertNull(DeckLoanMath.lentTo(cards, places, listOf(loan(2, 2, "Sam")))[70])
        assertEquals("Sam, Ana", DeckLoanMath.lentTo(cards, places, listOf(loan(2, 2, "Sam"), loan(3, 1, "Ana")))[70])
    }

    @Test fun edhrecPageIsReadLoosely() {
        val json = """{"container":{"json_dict":{"cardlists":[{"cardviews":[
            {"name":"Sol Ring","inclusion":900,"potential_decks":1000},
            {"name":"Arcane Signet","num_decks":500,"potential_decks":1000},
            {"name":"Sol Ring","inclusion":1,"potential_decks":1000},
            {"name":"No count"}]}]}}}"""
        val cards = DeckUpgradeMath.edhrecCards(json)
        assertEquals(listOf("Sol Ring", "Arcane Signet"), cards.map { it.first })
        assertEquals(0.9, cards[0].second!!, 0.001)
        assertTrue(DeckUpgradeMath.edhrecCards("not json").isEmpty())
    }

    @Test fun upgradeIdeasAreOwnedInColorsAndNew() {
        val owned = listOf(
            card(1, "Sol Ring"), card(2, "Counterspell", identity = "U"), card(3, "Lightning Bolt", identity = "R"),
            card(4, "Forest", type = "Basic Land — Forest"), card(5, "Arcane Signet"),
        )
        val ranked = listOf("Sol Ring", "Lightning Bolt", "Counterspell", "Forest", "Arcane Signet").map { it to null }
        val ideas = DeckUpgradeMath.ideas(ranked, owned, setOf(com.mtgscanner.util.CardNames.key("Arcane Signet")), "UG", { "Binder" to false })
        assertEquals(listOf("Sol Ring", "Counterspell"), ideas.map { it.name })
        assertEquals(listOf("Binder"), ideas[0].where)
        assertTrue(DeckUpgradeMath.fits("", "W"))
        assertFalse(DeckUpgradeMath.fits("WB", "W"))
    }

    @Test fun upgradeIdeasKnowTheirBoxesAndGroups() {
        val owned = listOf(card(1, "Sol Ring", box = 1, qty = 2), card(2, "Sol Ring", box = 9), card(3, "Counterspell", identity = "U", box = 9))
        val place = { c: CollectionCard -> if (c.boxId == 9L) "Atraxa" to true else "Binder" to false }
        val groups = DeckUpgradeMath.groupsOf(listOf(
            EdhrecGroup("Creatures", listOf(EdhrecRec("Sol Ring", null, null))),
            EdhrecGroup("Top Cards", listOf(EdhrecRec("Sol Ring", null, null))),
            EdhrecGroup("Instants", listOf(EdhrecRec("Counterspell", null, null))),
        ))
        val ranked = listOf("Sol Ring", "Counterspell").map { it to null }
        val ideas = DeckUpgradeMath.ideas(ranked, owned, emptySet(), "U", place, groups = groups)
        assertEquals(DeckUpgradeMath.TOP, ideas[0].group)
        assertEquals("Instants", ideas[1].group)
        // Boxes come before decks, so the first choice is a box.
        assertEquals(listOf("Binder", "Deck: Atraxa"), ideas[0].where)
        assertEquals(listOf(false, true), ideas[0].sources.map { it.inDeck })
        assertEquals("M10 1", ideas[0].sources[0].printing)
        // Cards only in decks can be left out.
        val skipped = DeckUpgradeMath.ideas(ranked, owned, emptySet(), "U", place, skipDeckOnly = true)
        assertEquals(listOf("Sol Ring"), skipped.map { it.name })
    }

    @Test fun boxBecomesADeckList() {
        val cards = listOf(card(1, "Opt", qty = 2, set = "xln", number = "65"), card(2, "Brainstorm", set = "ice", number = "61"))
        assertEquals("1 Brainstorm (ICE) 61\n2 Opt (XLN) 65", BoxToDeckMath.list(cards))
        assertEquals("1 Opt\n1 Brainstorm (ICE) 61", BoxToDeckMath.addMissing("1 Opt", cards))
        assertEquals("1 Opt\n1 Brainstorm", BoxToDeckMath.addMissing("1 Opt\n1 Brainstorm", cards))
        // A list ending with a sideboard: new cards go in the main deck, above it.
        assertEquals("1 Opt\n1 Brainstorm (ICE) 61\n\nSideboard\n1 Duress", BoxToDeckMath.addMissing("1 Opt\n\nSideboard\n1 Duress", cards))
        // A commander list: after the main deck's cards, not in the Commander section.
        assertEquals("Commander\n1 Atraxa\nDeck\n1 Opt\n1 Brainstorm (ICE) 61", BoxToDeckMath.addMissing("Commander\n1 Atraxa\nDeck\n1 Opt", cards))
    }
}
