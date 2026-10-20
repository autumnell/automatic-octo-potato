package com.mtgscanner.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardZoomTest {
    private val id = "0000579f-7b35-4ed3-b44c-db2a538066fe"

    @Test fun smallAndNormalBecomeLarge() {
        assertEquals(
            "https://cards.scryfall.io/large/front/0/0/$id.jpg?1562894979",
            largeCardImage("https://cards.scryfall.io/small/front/0/0/$id.jpg?1562894979"),
        )
        assertEquals(
            "https://cards.scryfall.io/large/back/0/0/$id.jpg",
            largeCardImage("https://cards.scryfall.io/normal/back/0/0/$id.jpg"),
        )
        assertEquals(
            "https://api.scryfall.com/cards/$id?format=image&version=large",
            largeCardImage("https://api.scryfall.com/cards/$id?format=image&version=small"),
        )
    }

    @Test fun otherLinksUnchanged() {
        assertNull(largeCardImage(null))
        assertEquals("https://cards.scryfall.io/png/front/0/0/$id.png", largeCardImage("https://cards.scryfall.io/png/front/0/0/$id.png"))
        assertEquals("/data/photo.jpg", largeCardImage("/data/photo.jpg"))
    }

    @Test fun largeLinkHasScryfallBackup() {
        assertEquals(
            "https://api.scryfall.com/cards/$id?format=image&version=large",
            scryfallImageFallback(largeCardImage("https://cards.scryfall.io/small/front/0/0/$id.jpg")),
        )
    }
}
