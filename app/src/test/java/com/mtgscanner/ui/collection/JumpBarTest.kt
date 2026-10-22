package com.mtgscanner.ui.collection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JumpBarTest {
    private val rail = listOf("#" to 0, "B" to 4, "D" to 20, "S" to 41)

    @Test fun letterWithCardsJumpsToItsHeader() {
        assertEquals(4, JumpBar.firstIndex(rail, "B"))
        assertEquals(41, JumpBar.firstIndex(rail, "S"))
        assertEquals(0, JumpBar.firstIndex(rail, "#"))
    }

    @Test fun missingLetterJumpsToTheNextOneThenTheLast() {
        assertEquals(4, JumpBar.firstIndex(rail, "A"))
        assertEquals(20, JumpBar.firstIndex(rail, "C"))
        assertEquals(41, JumpBar.firstIndex(rail, "M"))
        assertEquals(41, JumpBar.firstIndex(rail, "Z"))
    }

    @Test fun emptyRailHasNoJump() {
        assertNull(JumpBar.firstIndex(emptyList(), "A"))
    }

    @Test fun touchPositionPicksALetter() {
        assertEquals("#", JumpBar.letterAt(0f, 270f))
        assertEquals("A", JumpBar.letterAt(15f, 270f))
        assertEquals("Z", JumpBar.letterAt(269f, 270f))
        assertEquals("Z", JumpBar.letterAt(400f, 270f))
        assertEquals("#", JumpBar.letterAt(-5f, 270f))
    }

    @Test fun showsOnlyForLongNameSortedLists() {
        assertTrue(JumpBar.shows(true, 50, rail))
        assertFalse(JumpBar.shows(true, 49, rail))
        assertFalse(JumpBar.shows(false, 200, rail))
        assertFalse(JumpBar.shows(true, 200, emptyList()))
    }
}
