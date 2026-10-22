package com.mtgscanner.ui.collection

import org.junit.Assert.assertEquals
import org.junit.Test

class DividerTabMathTest {
    @Test fun tabsStepAcrossAndWrap() {
        assertEquals(0f, DividerTabMath.tabLeft(0, 180f, 60f), 0.001f)
        assertEquals(60f, DividerTabMath.tabLeft(1, 180f, 60f), 0.001f)
        assertEquals(120f, DividerTabMath.tabLeft(2, 180f, 60f), 0.001f)
        assertEquals(0f, DividerTabMath.tabLeft(3, 180f, 60f), 0.001f)
    }

    @Test fun lastTabEndsAtTheRightEdge() {
        val w = 178.6f
        val tab = 90f
        assertEquals(w, DividerTabMath.tabLeft(2, w, tab) + tab, 0.001f)
    }

    @Test fun tabWiderThanDividerSitsAtLeft() {
        assertEquals(0f, DividerTabMath.tabLeft(1, 50f, 80f), 0.001f)
    }
}
