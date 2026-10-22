package com.mtgscanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardLinksTest {
    @Test fun usesScryfallLinksWhenGiven() {
        val links = CardLinks.of(
            "Sol Ring",
            mapOf("edhrec" to "https://edhrec.com/route/?cc=Sol+Ring"),
            mapOf("tcgplayer" to "https://tcg.example/1", "cardmarket" to "https://cm.example/1"),
        )
        assertEquals(listOf("EDHREC", "TCGplayer", "Cardmarket"), links.map { it.label })
        assertEquals(listOf("https://edhrec.com/route/?cc=Sol+Ring", "https://tcg.example/1", "https://cm.example/1"), links.map { it.url })
    }

    @Test fun fallsBackToSearchesByFrontFace() {
        val links = CardLinks.of("Delver of Secrets // Insectile Aberration", emptyMap(), mapOf("tcgplayer" to "http://insecure"))
        assertEquals("https://edhrec.com/cards/delver-of-secrets", links[0].url)
        assertEquals("https://www.tcgplayer.com/search/magic/product?q=Delver+of+Secrets", links[1].url)
        assertTrue(links[2].url.endsWith("searchString=Delver+of+Secrets"))
    }

    @Test fun blankNameHasNoLinks() {
        assertTrue(CardLinks.of(" ", emptyMap(), emptyMap()).isEmpty())
    }
}
