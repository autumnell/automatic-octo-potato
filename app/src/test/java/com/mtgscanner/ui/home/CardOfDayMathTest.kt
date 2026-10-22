package com.mtgscanner.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardOfDayMathTest {
    private val ids = (1L..50L).toList()

    @Test fun emptyIsNull() = assertNull(CardOfDayMath.pick(emptyList(), 100))

    @Test fun sameAllDayWhateverTheOrder() {
        assertEquals(CardOfDayMath.pick(ids, 20000), CardOfDayMath.pick(ids.shuffled(), 20000))
    }

    @Test fun changesOverDays() {
        val picks = (20000L until 20030L).map { CardOfDayMath.pick(ids, it) }.toSet()
        assertTrue(picks.size > 5)
        assertTrue(picks.all { it in ids })
    }

    @Test fun keepsTodaysPickWhileOwned() {
        assertEquals(7L, CardOfDayMath.pick(ids, 20000, 20000L to 7L))
        // Yesterday's pick or a card no longer owned doesn't count.
        assertEquals(CardOfDayMath.pick(ids, 20000), CardOfDayMath.pick(ids, 20000, 19999L to 7L))
        assertEquals(CardOfDayMath.pick(ids, 20000), CardOfDayMath.pick(ids, 20000, 20000L to 999L))
    }
}
