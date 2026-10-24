package com.mtgscanner.ui.decks

import com.mtgscanner.util.CardNames
import com.mtgscanner.util.DeckLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedCardsTest {
    private fun l(name: String, q: Int = 1, section: String = "Main") = DeckLine(q, name, section = section)

    @Test fun findsCardsOtherDecksPlay() {
        val mine = listOf(l("Sol Ring"), l("Island", 30), l("Counterspell"), l("Lightning Bolt", section = "Sideboard"))
        val others = listOf(
            "Krenko" to listOf(l("Sol Ring"), l("Island", 10), l("Lightning Bolt")),
            "Atraxa" to listOf(l("sol ring"), l("Counterspell", section = "Maybeboard")),
        )
        // Basics, sideboard cards and other decks' maybeboards don't count.
        assertEquals(listOf(SharedCard("Sol Ring", listOf("Atraxa", "Krenko"))), SharedCards.of(mine, others))
    }

    @Test fun matchesTwoFacedNamesByFrontFace() {
        val mine = listOf(l("Delver of Secrets // Insectile Aberration"))
        val others = listOf("Tempo" to listOf(l("Delver of Secrets")))
        assertEquals(listOf("Tempo"), SharedCards.of(mine, others).single().decks)
    }

    @Test fun completeNeedsEveryPlayedCopy() {
        val lines = listOf(l("Sol Ring"), l("Forest", 2), l("Giant Growth", section = "Sideboard"))
        assertTrue(DeckOwnership.complete(lines, mapOf(CardNames.key("Sol Ring") to 1, CardNames.key("Forest") to 2)))
        assertFalse(DeckOwnership.complete(lines, mapOf(CardNames.key("Sol Ring") to 1, CardNames.key("Forest") to 1)))
        assertFalse(DeckOwnership.complete(emptyList(), emptyMap()))
    }
}
