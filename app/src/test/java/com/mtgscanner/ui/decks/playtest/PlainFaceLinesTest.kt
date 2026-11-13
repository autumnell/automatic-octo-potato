package com.mtgscanner.ui.decks.playtest

import org.junit.Assert.assertEquals
import org.junit.Test

class PlainFaceLinesTest {
    @Test fun longWordsStayWhole() {
        assertEquals(listOf("Gravecrawler"), PlainFaceLines.of("Gravecrawler"))
        assertEquals(listOf("Llanowar", "Elves"), PlainFaceLines.of("Llanowar Elves"))
    }

    @Test fun shortWordsShareALine() {
        assertEquals(listOf("Sol Ring"), PlainFaceLines.of("Sol Ring"))
        assertEquals(listOf("Swords to", "Plowshares"), PlainFaceLines.of("Swords to Plowshares"))
    }

    @Test fun atMostThreeLines() {
        assertEquals(listOf("Jace,", "Vryn's", "Prodigy Flipped Over"), PlainFaceLines.of("Jace, Vryn's Prodigy Flipped Over"))
    }
}
