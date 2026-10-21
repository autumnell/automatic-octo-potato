package com.mtgscanner.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveBackupReminderTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_000L * day

    @Test fun neverBackedUpShowsOnlyWithCards() {
        assertTrue(DriveBackupReminder.due(now, 0L, 0L, hasCards = true))
        assertFalse(DriveBackupReminder.due(now, 0L, 0L, hasCards = false))
    }

    @Test fun staleAfterTwoWeeks() {
        assertFalse(DriveBackupReminder.due(now, now - 14 * day, 0L, true))
        assertTrue(DriveBackupReminder.due(now, now - 15 * day, 0L, true))
    }

    @Test fun laterSnoozes() {
        assertFalse(DriveBackupReminder.due(now, 0L, now + day, true))
        assertTrue(DriveBackupReminder.due(now, 0L, now - 1, true))
    }

    @Test fun daysSince() {
        assertEquals(null, DriveBackupReminder.daysSince(now, 0L))
        assertEquals(20L, DriveBackupReminder.daysSince(now, now - 20 * day - 5))
    }
}
