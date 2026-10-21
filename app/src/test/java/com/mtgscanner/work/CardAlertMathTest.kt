package com.mtgscanner.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardAlertMathTest {
    private val base = CardPriceAlert(scryfallId = "a", foil = false, name = "Card", above = 10.0, below = 5.0)

    @Test fun firesOncePerCrossingAndRearms() {
        var a = CardAlertMath.create(base, 7.0)
        assertFalse(a.firedAbove); assertFalse(a.firedBelow)

        var r = CardAlertMath.check(a, 11.0)
        assertEquals(1, r.second.size)
        assertTrue(r.second[0].up)
        a = r.first
        assertTrue(a.firedAbove)

        // Still over: no second notice.
        r = CardAlertMath.check(a, 12.0)
        assertTrue(r.second.isEmpty())
        a = r.first

        // Back under the line re-arms; crossing again fires again.
        a = CardAlertMath.check(a, 9.0).first
        assertFalse(a.firedAbove)
        r = CardAlertMath.check(a, 10.5)
        assertEquals(1, r.second.size)
    }

    @Test fun underLine() {
        val a = CardAlertMath.create(base, 7.0)
        val r = CardAlertMath.check(a, 4.0)
        assertEquals(1, r.second.size)
        assertFalse(r.second[0].up)
        assertTrue(r.first.firedBelow)
        assertTrue(CardAlertMath.check(r.first, 3.0).second.isEmpty())
        assertFalse(CardAlertMath.check(r.first, 5.0).first.firedBelow)
    }

    @Test fun alreadyPastWhenCreatedWaitsForANewCrossing() {
        val a = CardAlertMath.create(base.copy(below = null), 15.0)
        assertTrue(a.firedAbove)
        assertTrue(CardAlertMath.check(a, 16.0).second.isEmpty())
    }

    @Test fun exactlyOnTheLineDoesNotFire() {
        val a = CardAlertMath.create(base, 7.0)
        assertTrue(CardAlertMath.check(a, 10.0).second.isEmpty())
        assertTrue(CardAlertMath.check(a, 5.0).second.isEmpty())
    }

    @Test fun unknownPriceKeepsAlert() {
        val a = CardAlertMath.create(base, 7.0).copy(firedAbove = true)
        val (next, hits) = CardAlertMath.checkAll(listOf(a), emptyMap())
        assertEquals(listOf(a), next)
        assertTrue(hits.isEmpty())
    }

    @Test fun foilIsSeparate() {
        val foil = base.copy(foil = true)
        val (_, hits) = CardAlertMath.checkAll(listOf(base, foil), mapOf(base.key to 7.0, foil.key to 20.0))
        assertEquals(1, hits.size)
        assertTrue(hits[0].alert.foil)
    }
}
