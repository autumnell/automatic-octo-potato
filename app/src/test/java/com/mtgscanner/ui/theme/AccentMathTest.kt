package com.mtgscanner.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccentMathTest {
    @Test fun emptyOrColorlessHasNoColor() {
        assertNull(AccentMath.dominant(emptyList()))
        assertNull(AccentMath.dominant(listOf("" to 40, "C" to 3)))
    }

    @Test fun countsCopiesNotRows() {
        assertEquals('G', AccentMath.dominant(listOf("U" to 1, "U" to 1, "G" to 3)))
    }

    @Test fun multicolorCountsForEachColor() {
        // Two Azorius cards and one red: white and blue tie at 2, white wins (WUBRG order).
        assertEquals('W', AccentMath.dominant(listOf("WU" to 2, "R" to 1)))
        assertEquals('B', AccentMath.dominant(listOf("BG" to 2, "G" to 0, "b" to 1)))
    }

    @Test fun repeatedLettersCountOnce() {
        assertEquals('R', AccentMath.dominant(listOf("UU" to 2, "R" to 3)))
    }

    @Test fun accentFallsBackToGold() {
        assertEquals(0xFFE6B422, AccentMath.accentArgb(null))
        assertEquals(0xFFE6B422, AccentMath.accentArgb('C'))
        assertEquals(AccentMath.accentArgb('U'), AccentMath.accentArgb('u'))
    }
}
