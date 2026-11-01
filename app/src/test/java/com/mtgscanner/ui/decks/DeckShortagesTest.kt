package com.mtgscanner.ui.decks

import com.mtgscanner.util.CardNames
import com.mtgscanner.util.DeckLine
import org.junit.Assert.assertEquals
import org.junit.Test

class DeckShortagesTest {
    @Test fun cardsInTwoDecksYouDontOwnEnoughOf() {
        val a = "Elves" to listOf(DeckLine(1, "Sol Ring"), DeckLine(1, "Forest"), DeckLine(1, "Llanowar Elves"), DeckLine(1, "Swords to Plowshares", section = "Sideboard"))
        val b = "Angels" to listOf(DeckLine(1, "Sol Ring"), DeckLine(10, "Forest"), DeckLine(1, "Swords to Plowshares"), DeckLine(1, "Llanowar Elves"))
        val owned = mapOf(CardNames.key("Sol Ring") to 1, CardNames.key("Llanowar Elves") to 2)
        val s = SharedCards.shortages(listOf(a, b), owned)
        // Llanowar Elves: 2 needed, 2 owned. Swords: only one deck plays it. Forest: basic.
        assertEquals(listOf("Sol Ring"), s.map { it.name })
        assertEquals(1, s[0].short)
        assertEquals(listOf("Angels" to 1, "Elves" to 1), s[0].decks)
    }
}
