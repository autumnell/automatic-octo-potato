package com.mtgscanner.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PluralTest {
    @Test fun one() = assertEquals("1 card", plural(1, "card"))
    @Test fun many() = assertEquals("3 cards", plural(3, "card"))
    @Test fun none() = assertEquals("0 cards", plural(0, "card"))
    @Test fun long() = assertEquals("1 game", plural(1L, "game"))
    @Test fun irregular() = assertEquals("2 copies · 1 copy", plural(2, "copy", "copies") + " · " + plural(1, "copy", "copies"))
}
