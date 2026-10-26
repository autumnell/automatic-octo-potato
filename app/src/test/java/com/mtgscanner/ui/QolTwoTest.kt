package com.mtgscanner.ui

import com.mtgscanner.data.FreshRule
import com.mtgscanner.ui.home.StatsMath
import com.mtgscanner.ui.settings.SettingsIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QolTwoTest {
    @Test
    fun freshMarksBigMovesAndCardsFromElsewhere() {
        val seen = mapOf(1L to 10.0, 2L to 10.0, 3L to 0.5, 4L to -1.0)
        val at = 1_000L
        assertTrue(FreshRule.isFresh(1, 10, 13.0, seen, at))
        assertFalse(FreshRule.isFresh(2, 10, 11.0, seen, at))
        // Cheap cards need a dollar of movement too.
        assertFalse(FreshRule.isFresh(3, 10, 0.9, seen, at))
        assertFalse(FreshRule.isFresh(4, 10, 5.0, seen, at))
        // Not seen before: older than the last look means it came from elsewhere; newer means it was just scanned.
        assertTrue(FreshRule.isFresh(9, 500, 1.0, seen, at))
        assertFalse(FreshRule.isFresh(9, 2_000, 1.0, seen, at))
        // Nothing marked before the first look.
        assertFalse(FreshRule.isFresh(9, 500, 1.0, emptyMap(), 0))
    }

    @Test
    fun settingsSearchFindsByNameAndKeyword() {
        assertEquals("Currency", SettingsIndex.search("euro").first().title)
        assertEquals("True black", SettingsIndex.search("true").first().title)
        assertTrue(SettingsIndex.search("  ").isEmpty())
        assertTrue(SettingsIndex.search("zzz").isEmpty())
    }

    @Test
    fun shareTextReadsLikeASentence() {
        assertEquals(
            "1,240 cards · \$3,180 · 62 sets · top card Ragavan, Nimble Pilferer \$54",
            StatsMath.shareText(1240, "\$3,180", 62, "Ragavan, Nimble Pilferer", "\$54"),
        )
        assertEquals("1 card · 1 set · top card Fire", StatsMath.shareText(1, null, 1, "Fire // Ice", null))
    }
}
