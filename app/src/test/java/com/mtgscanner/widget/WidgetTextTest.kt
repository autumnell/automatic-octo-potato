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

    @Test fun pricesUpdated() {
        val now = 10L * 24 * 60 * 60 * 1000
        assertEquals("", WidgetText.pricesUpdated(0, now))
        assertEquals("Prices updated 3h ago", WidgetText.pricesUpdated(now - 3 * 3_600_000, now))
        assertEquals("Prices updated just now", WidgetText.pricesUpdated(now, now))
    }
}
