package com.mtgscanner.data

import com.mtgscanner.util.CardNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeckInsightsDataTest {
    @Test fun readsTheAverageDeck() {
        val text = """{"deck":{"commander":["Atraxa, Praetors' Voice"],"cards":{"Artifact":[["Sol Ring",1],["Arcane Signet",1]],"Land":[["Forest",4],["Command Tower",1]]}},"container":{}}"""
        val d = AverageDeckMath.parse(text)!!
        assertEquals(listOf("Atraxa, Praetors' Voice"), d.commanders)
        assertEquals(listOf("Sol Ring" to 1, "Arcane Signet" to 1, "Forest" to 4, "Command Tower" to 1), d.cards)
    }

    @Test fun noDeckIsNull() {
        assertNull(AverageDeckMath.parse("""{"container":{}}"""))
        assertNull(AverageDeckMath.parse("not json"))
    }

    @Test fun findsCombosYouOwn() {
        val lines = sequenceOf(
            "1-2\tDramatic Reversal|Isochron Scepter\tInfinite mana|Infinite untap",
            "3-4-5\tThassa's Oracle|Demonic Consultation|Tainted Pact\tWin the game",
            "6-7\tFire // Ice|Sol Ring\tSomething",
            "bad line",
        )
        val owned = listOf("Dramatic Reversal", "Isochron Scepter", "Fire", "Sol Ring", "Thassa's Oracle").map(CardNames::key).toSet()
        val found = CollectionComboMath.owned(lines, owned, { if (it == CardNames.key("Sol Ring")) "Binder" else "" })
        assertEquals(listOf("1-2", "6-7"), found.map { it.id })
        assertEquals(listOf("Infinite mana", "Infinite untap"), found[0].produces)
        assertEquals("Fire // Ice" to "", found[1].pieces[0])
        assertEquals("Sol Ring" to "Binder", found[1].pieces[1])
    }

    @Test fun whereTextSumsAndSorts() {
        assertEquals("Binder ×3 · Deck: Atraxa", CollectionComboMath.whereText(listOf("Deck: Atraxa" to 1, "Binder" to 1, "Binder" to 2)))
    }
}
