package com.mtgscanner.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ManaSpeechTest {
    @Test fun saysColorsAndNumbers() {
        assertEquals("2, blue, blue", ManaSpeech.of("{2}{U}{U}"))
        assertEquals("X, red", ManaSpeech.of("{X}{R}"))
        assertEquals("white or blue", ManaSpeech.of("{W/U}"))
        assertEquals("Phyrexian black", ManaSpeech.of("{B/P}"))
        assertEquals("1, green or 3, red", ManaSpeech.of("{1}{G} // {3}{R}"))
    }
}
