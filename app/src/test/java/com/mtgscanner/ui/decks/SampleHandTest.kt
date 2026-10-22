package com.mtgscanner.ui.decks

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SampleHandTest {
    private val deck = SampleHandMath.copies((1..20).map { "Card $it" to 3 })

    @Test fun copiesExpands() {
        assertEquals(listOf("A", "A", "B"), SampleHandMath.copies(listOf("A" to 2, "B" to 1, "C" to 0)))
    }

    @Test fun dealsSevenAndKeepsEveryCard() {
        val h = SampleHandMath.deal(deck, Random(1))
        assertEquals(7, h.hand.size)
        assertEquals(53, h.library.size)
        assertEquals(deck.sorted(), (h.hand + h.library).sorted())
        assertEquals(0, h.mulligans)
    }

    @Test fun sameSeedSameHand() {
        assertEquals(SampleHandMath.deal(deck, Random(42)), SampleHandMath.deal(deck, Random(42)))
    }

    @Test fun mulliganReshufflesAndCounts() {
        val r = Random(7)
        val first = SampleHandMath.deal(deck, r)
        val second = SampleHandMath.mulligan(first, r)
        assertEquals(1, second.mulligans)
        assertEquals(7, second.hand.size)
        assertEquals(deck.sorted(), (second.hand + second.library).sorted())
        assertNotEquals(first.hand + first.library, second.hand + second.library)
        assertEquals(2, SampleHandMath.mulligan(second, r).mulligans)
    }

    @Test fun drawTakesTheTopCard() {
        val h = SampleHandMath.deal(deck, Random(3))
        val d = SampleHandMath.draw(h)
        assertEquals(8, d.hand.size)
        assertEquals(h.library.first(), d.hand.last())
        assertEquals(h.library.drop(1), d.library)
    }

    @Test fun smallDeckAndEmptyLibrary() {
        val h = SampleHandMath.deal(listOf("A", "B", "C"), Random(0))
        assertEquals(3, h.hand.size)
        assertFalse(h.canDraw)
        assertSame(h, SampleHandMath.draw(h))
    }
}
