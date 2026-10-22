package com.mtgscanner.data

import com.mtgscanner.util.DecklistParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckAddTest {
    private val key = { n: String -> n.lowercase() }
    private val list = DecklistParser.parse(
        "Commander\n1 Atraxa, Praetors' Voice (ONE) 196 *F*\n\nDeck\n4 Lightning Bolt (M10) 146\n1 Sol Ring\n\nSideboard\n2 Lightning Bolt\n\nMaybeboard\n1 Duress",
    )

    @Test fun allAddsEverythingAsListed() {
        val rows = DeckAdd.rows(list, mapOf("lightning bolt" to 10), onlyMissing = false, key = key)
        assertEquals(listOf("Atraxa, Praetors' Voice", "Lightning Bolt", "Sol Ring", "Lightning Bolt"), rows.map { it.name })
        assertEquals(listOf(1, 4, 1, 2), rows.map { it.quantity })
        assertEquals("one", rows[0].setCode)
        assertEquals("196", rows[0].collectorNumber)
        assertTrue(rows[0].foil)
        assertEquals("m10", rows[1].setCode)
    }

    @Test fun onlyMissingSkipsOwnedCopies() {
        // 5 Bolts owned: the main deck's 4 first, then 1 of the sideboard's 2.
        val rows = DeckAdd.rows(list, mapOf("lightning bolt" to 5, "sol ring" to 1), onlyMissing = true, key = key)
        assertEquals(listOf("Atraxa, Praetors' Voice", "Lightning Bolt"), rows.map { it.name })
        assertEquals(listOf(1, 1), rows.map { it.quantity })
        assertTrue(DeckAdd.rows(list, mapOf("lightning bolt" to 9, "sol ring" to 1, "atraxa, praetors' voice" to 1), true, key).isEmpty())
    }

    @Test fun resultLine() {
        assertEquals("Added 58 · 2 not found", DeckAdd.resultLine(58, 2))
        assertEquals("Added 1 card", DeckAdd.resultLine(1, 0))
    }
}
