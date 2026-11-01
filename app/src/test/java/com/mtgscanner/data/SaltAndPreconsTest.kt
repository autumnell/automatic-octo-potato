package com.mtgscanner.data

import com.mtgscanner.util.CardNames
import com.mtgscanner.util.DeckLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SaltAndPreconsTest {
    @Test fun saltPageAnyShape() {
        val first = """{"container":{"json_dict":{"cardlists":[{"cardviews":[{"name":"Stasis","salt":3.06,"id":"x"},{"name":"Armageddon","salt":2.9}]}]}}}"""
        val later = """{"cardviews":[{"name":"Sol Ring","salt":1.2}]}"""
        assertEquals(listOf("Stasis" to 3.06, "Armageddon" to 2.9), SaltMath.parse(first))
        assertEquals(listOf("Sol Ring" to 1.2), SaltMath.parse(later))
        assertEquals(emptyList<Pair<String, Double>>(), SaltMath.parse("not json"))
    }

    @Test fun deckSalt() {
        val salt = mapOf(CardNames.key("Stasis") to 3.0, CardNames.key("Sol Ring") to 1.5, CardNames.key("Duress") to 0.5)
        val lines = listOf(DeckLine(1, "Stasis"), DeckLine(1, "Sol Ring"), DeckLine(1, "Sol Ring", "c21", "263"), DeckLine(1, "Duress"), DeckLine(1, "Bolt", section = "Sideboard"))
        val d = SaltMath.deck(lines, salt)!!
        assertEquals(5.0, d.total, 1e-9)
        assertEquals(listOf("Stasis", "Sol Ring"), d.top.map { it.first })
        assertNull(SaltMath.deck(listOf(DeckLine(1, "Bolt")), salt))
    }

    @Test fun preconList() {
        val text = """{"data":[{"code":"C21","name":"Old","fileName":"Old_C21","type":"Commander Deck","releaseDate":"2021-04-23"},
            {"code":"MOC","name":"New","fileName":"New_MOC","type":"Commander Deck","releaseDate":"2023-04-21"},{"name":"broken"}]}"""
        val l = PreconJson.list(text)
        assertEquals(listOf("New", "Old"), l.map { it.name })
        assertEquals(true, l[0].isCommander)
    }

    @Test fun preconDeck() {
        val e = PreconEntry("C21", "Old", "Old_C21", "Commander Deck", "2021")
        val text = """{"data":{"commander":[{"name":"Atraxa","count":1,"setCode":"C21","number":"1","isFoil":true}],
            "mainBoard":[{"name":"Sol Ring","count":1,"setCode":"C21","number":"263"},{"name":"Sol Ring","count":1,"setCode":"C21","number":"263","isFoil":true},{"name":"Forest","count":30}],
            "sideBoard":[]}}"""
        val d = PreconJson.deck(e, text)!!
        assertEquals(33, d.total)
        assertEquals(2, d.cards.first { it.name == "Sol Ring" }.count)
        assertEquals(listOf("Forest" to 30, "Sol Ring" to 1), d.missing(mapOf(CardNames.key("Sol Ring") to 1, CardNames.key("Atraxa") to 1)))
        assertEquals(2, d.ownedCopies(mapOf(CardNames.key("Sol Ring") to 1, CardNames.key("Atraxa") to 1)))
        assertEquals("Commander\n1 Atraxa (C21) 1\n\nDeck\n2 Sol Ring (C21) 263\n30 Forest", d.asText())
    }
}
