package com.mtgscanner.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AlmostDecksTest {
    @Test fun typicalDeckLeavesOutCommanderBasicsAndRareCards() {
        val ranked = listOf(
            "Atraxa, Praetors' Voice" to 1.0, "Sol Ring" to 0.9, "Forest" to 0.8, "Kodama's Reach" to 0.5,
            "Some Rare Pick" to 0.05, "Similar Commander" to null, "Sol Ring" to 0.9,
        )
        assertEquals(listOf("Sol Ring", "Kodama's Reach"), AlmostDeckMath.typical("Atraxa, Praetors' Voice", ranked))
    }

    @Test fun mostOwnedFirstAndSmallListsLeftOut() {
        val forty = List(40) { "Card $it" }
        val a = AlmostDeck("A", false, forty, forty.take(10).map { com.mtgscanner.util.CardNames.key(it) }.toSet())
        val b = AlmostDeck("B", true, forty, forty.take(30).map { com.mtgscanner.util.CardNames.key(it) }.toSet())
        val tiny = AlmostDeck("C", true, forty.take(5), emptySet())
        assertEquals(listOf("B", "A"), AlmostDeckMath.rank(listOf(a, tiny, b)).map { it.commander })
        assertEquals(10, b.missing.size)
        assertEquals(0.75, b.share, 1e-9)
    }
}
