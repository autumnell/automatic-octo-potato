package com.mtgscanner.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DeckCutMathTest {
    @Test fun cardsEdhrecNeverPlaysComeFirstThenTheLeastPlayed() {
        val deck = listOf(
            "Sol Ring" to "Artifact", "Forest" to "Basic Land — Forest", "Pet Rock" to "Creature",
            "Llanowar Elves" to "Creature — Elf Druid", "Omnath, Locus of Mana" to "Legendary Creature", "Gaea's Gift" to "Instant",
        )
        val shares = mapOf("Sol Ring" to 0.9, "Llanowar Elves" to 0.4, "Gaea's Gift" to 0.02).mapKeys { com.mtgscanner.util.CardNames.key(it.key) }
        val cuts = DeckCutMath.cuts(deck, listOf("Omnath, Locus of Mana"), shares, emptyMap())
        assertEquals(listOf("Pet Rock", "Gaea's Gift", "Llanowar Elves", "Sol Ring"), cuts.map { it.name })
        assertEquals(false, cuts.first().onPage)
    }
}
