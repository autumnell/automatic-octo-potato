package com.mtgscanner.util

import org.junit.Assert.assertEquals
import org.junit.Test

class BuyListTest {
    private val cards = listOf("Lightning Bolt" to 2, "Delver of Secrets // Insectile Aberration" to 1, "lightning bolt" to 1, "Nope" to 0)

    @Test fun formats() {
        assertEquals("3 Lightning Bolt\n1 Delver of Secrets // Insectile Aberration", BuyList.text(cards, BuyStore.TCGPLAYER))
        assertEquals("3 Lightning Bolt\n1 Delver of Secrets", BuyList.text(cards, BuyStore.CARD_KINGDOM))
        assertEquals("3x Lightning Bolt\n1x Delver of Secrets // Insectile Aberration", BuyList.text(cards, BuyStore.CARDMARKET))
    }
}
