package com.mtgscanner.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsIndexTest {
    @Test
    fun findsTheDeckAndRestoreRows() {
        assertEquals("Skip cards only in decks", SettingsIndex.search("skip").first().title)
        assertEquals("Undo last restore", SettingsIndex.search("undo").first().title)
        assertTrue(SettingsIndex.entries.all { it.tab in 0..2 })
    }
}
