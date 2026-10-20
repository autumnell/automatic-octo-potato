package com.mtgscanner.scan

import org.junit.Assert.assertEquals
import org.junit.Test

class DeckTextTest {
    @Test fun emptyDeck() {
        assertEquals("1 Lightning Bolt", DeckText.adjust("", "Lightning Bolt", 1))
        assertEquals("", DeckText.adjust("", "Lightning Bolt", -1))
    }

    @Test fun bumpsExistingLine() {
        val text = "4 Goblin Guide\n2x Lightning Bolt\n\nSideboard\n2 Lightning Bolt"
        assertEquals("4 Goblin Guide\n3x Lightning Bolt\n\nSideboard\n2 Lightning Bolt", DeckText.adjust(text, "Lightning Bolt", 1))
        assertEquals("4 Goblin Guide\n\nSideboard\n2 Lightning Bolt", DeckText.adjust(text, "Lightning Bolt", -2))
        assertEquals(2, DeckText.count(text, "Lightning Bolt"))
    }

    @Test fun addsNewCardToMainDeck() {
        val text = "4 Goblin Guide\n\nSideboard\n2 Duress"
        assertEquals("4 Goblin Guide\n1 Shock\n\nSideboard\n2 Duress", DeckText.adjust(text, "Shock", 1))
        // Not in the main deck: nothing to take away.
        assertEquals(text, DeckText.adjust(text, "Duress", -1))
    }

    @Test fun sideboardCopiesAreLeftAlone() {
        val text = "Sideboard\n2 Duress"
        assertEquals("1 Duress\nSideboard\n2 Duress", DeckText.adjust(text, "Duress", 1))
    }
}
