package com.mtgscanner.ui.collection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuizMathTest {
    private val cards = listOf(
        QuizCard(1, "Bolt", 10, 1.0),
        QuizCard(2, "Ragavan", 11, 50.0),
        QuizCard(3, "Island", 12, 0.02),
        QuizCard(4, "Bolt", 11, 1.02),
    )

    @Test fun boxOptionsHoldTheAnswer() {
        repeat(50) { seed ->
            val q = QuizMath.whichBox(cards, listOf(10, 11, 12, 13, 14), Random(seed))!!
            assertTrue(q.card.boxId in q.options)
            assertEquals(4, q.options.size)
            assertEquals(q.options.size, q.options.distinct().size)
        }
    }

    @Test fun needsTwoBoxes() = assertNull(QuizMath.whichBox(cards, listOf(10), Random(1)))

    @Test fun pricePairsDiffer() {
        repeat(50) { seed ->
            val q = QuizMath.higherLower(cards, Random(seed))!!
            assertTrue(q.card.name != q.shown.name)
            assertTrue(QuizMath.farEnough(q.card.price!!, q.shown.price!!))
        }
        assertNull(QuizMath.higherLower(listOf(cards[0], cards[3]), Random(1)))
    }

    @Test fun mixFallsBack() {
        assertNotNull(QuizMath.next(QuizMode.MIX, cards, listOf(10), Random(3)))
        assertNull(QuizMath.next(QuizMode.BOX, cards, listOf(10), Random(3)))
    }

    @Test fun scoreKeepsBestStreak() {
        val s = QuizScore(best = 1).answer(true).answer(true).answer(false).answer(true)
        assertEquals(QuizScore(3, 4, 1, 2), s)
        assertFalse(QuizMath.farEnough(1.0, 1.04))
    }
}
