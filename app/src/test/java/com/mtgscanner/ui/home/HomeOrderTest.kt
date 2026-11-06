package com.mtgscanner.ui.home

import com.mtgscanner.ui.home.HomeSection.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeOrderTest {
    private val oldKnown = "sort,goals,daycard,recent,movers,valuable,gainers,boxes,bulk,colors,pairs,alerts"

    @Test fun newInstallStartsWithStripThenTiles() {
        val list = HomeOrder.load(null, null)
        assertEquals(listOf(NEWEST, TILES), list.take(2))
        assertFalse(RECENT in list)
    }

    @Test fun existingUserGetsStripFirstAndTilesUnderIt() {
        val list = HomeOrder.load("goals,recent,movers", oldKnown)
        // Sections added since that save (Recently viewed) come in at the end.
        assertEquals(listOf(NEWEST, TILES, GOALS, RECENT, MOVERS, VIEWED), list)
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

    @Test fun removedSectionsAreDropped() {
        val known = "newest,tiles,sort,goals,daycard,recent,movers,valuable,gainers,boxes,bulk,colors,pairs,alerts"
        val list = HomeOrder.load("newest,daycard,tiles,valuable,sort,colors,pairs,boxes,bulk,goals,alerts,someday", known)
        assertEquals(listOf(NEWEST, TILES, TO_SORT, GOALS, ALERTS, VIEWED), list)
    }

    @Test fun biggestGainsBecomesPriceMoves() {
        val known = "newest,tiles,sort,goals,daycard,recent,movers,valuable,gainers,boxes,bulk,colors,pairs,alerts"
        assertEquals(listOf(NEWEST, MOVERS, GOALS, VIEWED), HomeOrder.load("newest,gainers,goals", known))
        assertEquals(listOf(MOVERS, GOALS, VIEWED), HomeOrder.load("movers,gainers,goals", known))
    }
}
