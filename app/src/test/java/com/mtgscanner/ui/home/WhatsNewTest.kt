package com.mtgscanner.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WhatsNewTest {
    private val notes = mapOf(1 to "One", 200 to "Two")

    @Test fun showsTheNewestLineSinceLastClosed() {
        assertEquals("Two", WhatsNew.lineFor(250, 0, notes))
        assertEquals("Two", WhatsNew.lineFor(200, 150, notes))
        assertEquals("One", WhatsNew.lineFor(150, 0, notes))
    }

    @Test fun nothingOnceClosedOrWithoutANewLine() {
        assertNull(WhatsNew.lineFor(250, 250, notes))
        assertNull(WhatsNew.lineFor(260, 250, notes))
        assertNull(WhatsNew.lineFor(150, 100, notes))
    }

    @Test fun shippedNotesShowToUpgradingUsers() {
        assertEquals(WhatsNew.NOTES.values.last(), WhatsNew.lineFor(WhatsNew.NOTES.keys.max(), 0))
    }
}
