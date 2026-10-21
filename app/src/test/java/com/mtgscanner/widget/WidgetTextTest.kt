package com.mtgscanner.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetTextTest {
    @Test fun updatedAgo() {
        val now = 10L * 24 * 60 * 60 * 1000
        assertEquals("", WidgetText.updated(0, now))
        assertEquals("Updated just now", WidgetText.updated(now - 20_000, now))
        assertEquals("Updated 5m ago", WidgetText.updated(now - 5 * 60_000, now))
        assertEquals("Updated 3h ago", WidgetText.updated(now - 3 * 3_600_000, now))
        assertEquals("Updated 2d ago", WidgetText.updated(now - 49 * 3_600_000, now))
    }

    @Test fun moversBiggestFirst() {
        val now = listOf(
            Triple("a:false", "Bolt", 3.0), Triple("b:false", "Ring", 1.0), Triple("c:true", "Delver", 10.0),
            Triple("d:false", "New", 5.0), Triple("e:false", "Flat", 2.1),
        )
        val then = mapOf("a:false" to 2.0, "b:false" to 4.0, "c:true" to 9.5, "e:false" to 2.0)
        val m = WidgetText.movers(now, then)
        assertEquals(listOf("Ring", "Bolt", "Delver"), m.map { it.name })
        assertEquals("−$3.00", WidgetText.change(m[0].change))
        assertEquals("+$1.00", WidgetText.change(m[1].change))
    }
}
