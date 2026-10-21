package com.mtgscanner.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationTextTest {
    @Test fun nightHours() {
        assertTrue(Notifications.isNight(3))
        assertTrue(Notifications.isNight(22))
        assertFalse(Notifications.isNight(8))
        assertFalse(Notifications.isNight(21))
    }

    @Test fun digest() {
        assertEquals("$1,234.00 · up $34.00 this week · 812 cards · 12 new this week", Notifications.digestText(1234.0, 1200.0, 812, 800, true))
        assertEquals("812 cards", Notifications.digestText(1234.0, null, 812, null, false))
    }
}
