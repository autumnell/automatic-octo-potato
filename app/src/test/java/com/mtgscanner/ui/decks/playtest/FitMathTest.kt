package com.mtgscanner.ui.decks.playtest

import org.junit.Assert.assertEquals
import org.junit.Test

class FitMathTest {
    @Test fun spacedWhenTheyFitOverlappedWhenNot() {
        assertEquals(80f, FitMath.step(3, 76f, 4f, 360f), 0f)
        // Ten cards of 76 in 360: the last one ends at the edge.
        val step = FitMath.step(10, 76f, 4f, 360f)
        assertEquals(360f, step * 9 + 76f, 0.01f)
        assertEquals(80f, FitMath.step(1, 76f, 4f, 10f), 0f)
    }
}
