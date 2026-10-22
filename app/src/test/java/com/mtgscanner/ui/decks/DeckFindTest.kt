package com.mtgscanner.ui.decks

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckFindTest {
    @Test fun matches() {
        assertTrue(DeckFind.matches("Sol Ring", ""))
        assertTrue(DeckFind.matches("Sol Ring", "  "))
        assertTrue(DeckFind.matches("Sol Ring", "ring"))
        assertTrue(DeckFind.matches("Atraxa, Praetors' Voice", "praetors voice"))
        assertTrue(DeckFind.matches("Fire // Ice", "ice"))
        assertFalse(DeckFind.matches("Sol Ring", "bolt"))
        assertFalse(DeckFind.matches("Sol Ring", "!!"))
    }
}
