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

    @Test fun pickingAPrintingKeepsTags() {
        val text = "1 Sol Ring (CMR) 263 #Ramp #!Staple\n1 Arcane Signet ^Have,#37d67a^\n2 Duress (M19) 94 *F* [Removal]\n1 Forest [THB]"
        val a = DeckText.setPrinting(text, "Sol Ring", "cmr", "263", "cmr", "472")
        assertEquals("1 Sol Ring (CMR) 472 #Ramp #!Staple", a.lines()[0])
        val b = DeckText.setPrinting(text, "Arcane Signet", null, null, "cmm", "374")
        assertEquals("1 Arcane Signet (CMM) 374 ^Have,#37d67a^", b.lines()[1])
        val c = DeckText.setPrinting(text, "Duress", "m19", "94", "xln", "105")
        assertEquals("2 Duress (XLN) 105 *F* [Removal]", c.lines()[2])
        // "[THB]" after the name is the set, not a tag.
        assertEquals(emptyList<String>(), DeckText.tags("1 Forest [THB]"))
        assertEquals(listOf("#Card Draw"), DeckText.tags("1 Rhystic Study (PCY) 45 #Card Draw *F*"))
    }
}
