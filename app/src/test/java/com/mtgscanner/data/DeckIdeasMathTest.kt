package com.mtgscanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckIdeasMathTest {
    // Trimmed from json.edhrec.com/pages/commanders/atraxa-praetors-voice.json.
    private val edhrec = """
        {"header":"Atraxa","container":{"json_dict":{"card":{},"cardlists":[
          {"tag":"newcards","header":"New Cards","cardviews":[{"name":"Loyal Tutor","num_decks":555,"potential_decks":6359}]},
          {"tag":"topcards","header":"Top Cards","cardviews":[
            {"name":"Sol Ring","num_decks":5000,"potential_decks":5648,"synergy":0.01},
            {"name":"Sanctum Lurker","num_decks":580,"potential_decks":5648,"synergy":0.0418}]},
          {"tag":"lands","header":"Lands","cardviews":[]}
        ]}}}
    """.trimIndent()

    @Test fun edhrecListsWithShares() {
        val groups = DeckIdeasMath.edhrecGroups(edhrec)
        assertEquals(listOf("Top Cards"), groups.map { it.header })
        val lurker = groups[0].cards[1]
        assertEquals("Sanctum Lurker", lurker.name)
        assertEquals(580.0 / 5648, lurker.share!!, 1e-9)
        assertEquals(0.0418, lurker.synergy!!, 1e-9)
        assertTrue(DeckIdeasMath.edhrecGroups("not json").isEmpty())
    }

    private val spellbook = """
        {"count":2,"next":null,"previous":null,"results":{"identity":"U",
          "included":[{"id":"4821-5261","uses":[{"card":{"name":"Dramatic Reversal"}},{"card":{"name":"Isochron Scepter"}}],
            "produces":[{"feature":{"name":"Infinite magecraft triggers"}},{"feature":{"name":"Infinite mana nonland permanents you control can produce"}}]}],
          "almostIncluded":[{"id":"1-2","uses":[{"card":{"name":"Thrasios, Triton Hero"}},{"card":{"name":"Freed from the Real"}}],"produces":[]}],
          "almostIncludedByAddingColors":[]}}
    """.trimIndent()

    @Test fun spellbookCombos() {
        val have = setOf("dramatic reversal", "isochron scepter", "thrasios, triton hero")
        val c = DeckIdeasMath.combos(spellbook) { it.lowercase() in have }!!
        assertEquals(1, c.included.size)
        assertEquals(listOf("Dramatic Reversal", "Isochron Scepter"), c.included[0].cards)
        assertTrue(c.included[0].missing.isEmpty())
        assertEquals("Infinite magecraft triggers", c.included[0].produces[0])
        assertEquals(listOf("Freed from the Real"), c.almost[0].missing)
        assertNull(DeckIdeasMath.combos("{}") { true })
    }

    @Test fun spellbookBody() {
        val body = DeckIdeasMath.spellbookBody(listOf("Thrasios, Triton Hero"), listOf("Island" to 10))
        assertEquals("""{"commanders":[{"card":"Thrasios, Triton Hero","quantity":1}],"main":[{"card":"Island","quantity":10}]}""", body)
    }
}
