package com.mtgscanner.ui.home

import com.mtgscanner.ui.home.HomeSection.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeOrderTest {
    private val oldKnown = listOf(TO_SORT, GOALS, CARD_OF_DAY, RECENT, MOVERS, VALUABLE, GAINERS, BOX_VALUES, BULK, COLORS, PAIRS, ALERTS)
        .joinToString(",") { it.key }

    @Test fun newInstallStartsWithStripThenTiles() {
        val list = HomeOrder.load(null, null)
        assertEquals(listOf(NEWEST, TILES), list.take(2))
        assertFalse(RECENT in list)
    }

    @Test fun existingUserGetsStripFirstAndTilesUnderIt() {
        val list = HomeOrder.load("goals,recent,colors", oldKnown)
        assertEquals(listOf(NEWEST, TILES, GOALS, RECENT, COLORS), list)
    }

    @Test fun hiddenStripStaysHiddenOnceSavedWithIt() {
        val known = HomeSection.entries.joinToString(",") { it.key }
        assertEquals(listOf(GOALS, TILES), HomeOrder.load("goals,tiles", known))
    }

    @Test fun movedStripKeepsItsPlace() {
        val known = HomeSection.entries.joinToString(",") { it.key }
        assertEquals(listOf(TILES, GOALS, NEWEST), HomeOrder.load("tiles,goals,newest", known))
    }

    @Test fun veryOldSaveWithoutKnownListStillGetsStripFirst() {
        val list = HomeOrder.load("goals", null)
        assertEquals(listOf(NEWEST, TILES, GOALS), list.take(3))
        assertEquals(HomeSection.entries.size, list.size)
    }
}
