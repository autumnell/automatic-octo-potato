package com.mtgscanner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanTrackingTest {

    @Test
    fun glareFramesDontCountAsLeaving() {
        val t = LeaveTracker(emptyNeeded = 3, blankNeeded = 12)
        // Tilted for glare: a few frames with a card but no text.
        repeat(5) { assertFalse(t.nothing(guideEmpty = false)) }
        t.seen()
        repeat(2) { assertFalse(t.nothing(guideEmpty = true)) }
        assertTrue(t.nothing(guideEmpty = true))
    }

    @Test
    fun longBlankRunCountsAsLeaving() {
        val t = LeaveTracker(emptyNeeded = 3, blankNeeded = 4)
        repeat(3) { assertFalse(t.nothing(guideEmpty = false)) }
        assertTrue(t.nothing(guideEmpty = false))
    }

    @Test
    fun samePrintingIsSameCard() {
        assertTrue(sameCardInView("a", "o", true, "a", "o", true))
    }

    @Test
    fun nameOnlyReadOfAnotherPrintingIsSameCard() {
        assertTrue(sameCardInView("a", "o", true, "b", "o", false))
        assertTrue(sameCardInView("a", "o", false, "b", "o", true))
    }

    @Test
    fun twoExactFootersAreTwoCards() {
        // Forest from ZEN, then Forest from MOM, both read by footer.
        assertFalse(sameCardInView("zen-forest", "forest", true, "mom-forest", "forest", true))
    }

    @Test
    fun otherNameIsOtherCard() {
        assertFalse(sameCardInView("a", "o", false, "b", "p", false))
        assertFalse(sameCardInView(null, null, false, "b", null, false))
    }

    private fun g(year: Int? = null, hash: Long? = null) =
        CardTextGuess(listOf("Llanowar Elves"), null, null, 10, year = year, artHash = hash)

    @Test
    fun poolsYearByVote() {
        val p = FramePool()
        p.add(g(year = 1997))
        p.add(g(year = 1999))
        p.add(g(year = 1997))
        assertEquals(1997, p.pooled(g(year = 1999)).year)
    }

    @Test
    fun poolsArtByMedoid() {
        val p = FramePool()
        val a = 0x0F0F0F0F0F0F0F0FL
        val near = a xor 0x3L
        val outlier = a.inv()
        p.add(g(hash = a))
        p.add(g(hash = outlier))
        p.add(g(hash = near))
        val h = p.pooled(g(hash = outlier)).artHash!!
        assertTrue(java.lang.Long.bitCount(h xor a) <= 2)
        assertTrue(p.complete.not())
    }

    @Test
    fun emptyPoolKeepsGuess() {
        val p = FramePool()
        val guess = g(year = 2001, hash = 5L)
        assertEquals(2001, p.pooled(guess).year)
        assertEquals(5L, p.pooled(guess).artHash)
        assertNull(FramePool.mostCommon(emptyList<Int>()))
    }
}
