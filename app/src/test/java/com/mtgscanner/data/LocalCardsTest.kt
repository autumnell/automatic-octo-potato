package com.mtgscanner.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalCardsTest {
    private fun card(id: String, lang: String = "en", promo: Boolean = false, day: String = "2020-01-01") =
        ScryfallCard(id = id, name = "Opt", lang = lang, releasedAt = day, set = "xln", setName = "Ixalan", collectorNumber = "1", rarity = "common", promo = promo)

    @Test fun nameStandsForTheNewestRegularEnglishPrinting() {
        val pick = LocalPick.newest(listOf(card("old"), card("new", day = "2024-01-01"), card("promo", promo = true, day = "2025-01-01"), card("ja", lang = "ja", day = "2026-01-01")))
        assertEquals("new", pick?.id)
    }

    @Test fun packedCardsComeBack() {
        val s = "{\"id\":\"abc\",\"name\":\"Ætherize\"}".repeat(20)
        assertEquals(s, LocalCards.unpack(LocalCards.pack(s)))
    }

    @Test fun newCardsAreAskedForFromTheDayBefore() {
        assertEquals("2026-10-23", LocalPick.since("2026-10-24"))
    }

    @Test fun slimDropsPageOnlyFields() {
        val c = card("a").copy(legalities = mapOf("modern" to "legal"), relatedUris = mapOf("edhrec" to "x"))
        val slim = LocalPick.slim(c)
        assertEquals(emptyMap<String, String>(), slim.legalities)
        assertEquals(emptyMap<String, String>(), slim.relatedUris)
        assertEquals("a", slim.id)
    }
}
