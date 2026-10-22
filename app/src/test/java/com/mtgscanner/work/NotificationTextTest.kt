package com.mtgscanner.work

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
}
