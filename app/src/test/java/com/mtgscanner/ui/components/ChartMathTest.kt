package com.mtgscanner.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ChartMathTest {
    @Test fun nearestPointUnderTheFinger() {
        assertEquals(0, ChartMath.indexAt(0f, 100f, 5))
        assertEquals(2, ChartMath.indexAt(50f, 100f, 5))
        assertEquals(1, ChartMath.indexAt(30f, 100f, 5))
        assertEquals(4, ChartMath.indexAt(100f, 100f, 5))
        // Off either end stays on the chart.
        assertEquals(0, ChartMath.indexAt(-20f, 100f, 5))
        assertEquals(4, ChartMath.indexAt(140f, 100f, 5))
        assertEquals(0, ChartMath.indexAt(10f, 0f, 5))
    }
}
