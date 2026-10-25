package com.mtgscanner.scan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardLookTest {
    private val w = 40
    private val h = 50

    /** A grid with a card at (5, 5)-(35, 45): a light frame around [art]'s picture. */
    private fun grid(art: (Int, Int) -> Int, brighter: Int = 0, shift: Int = 0): IntArray = IntArray(w * h) { i ->
        val x = i % w - shift
        val y = i / w
        val v = when {
            x !in 5 until 35 || y !in 5 until 45 -> 20
            x in 8 until 32 && y in 9 until 26 -> art(x, y)
            else -> 200
        }
        (v + brighter).coerceIn(0, 255)
    }

    private val sky: (Int, Int) -> Int = { _, y -> if (y < 17) 230 else 60 }
    private val forest: (Int, Int) -> Int = { x, _ -> if (x < 20) 40 else 160 }

    @Test fun sameCardBrighterIsTheSame() {
        val a = CardLook.of(grid(sky), w, 5, 5, 35, 45, h)!!
        val b = CardLook.of(grid(sky, brighter = 25), w, 5, 5, 35, 45, h)!!
        assertFalse(CardLook.differs(a, b))
    }

    @Test fun nudgedCardIsTheSame() {
        val a = CardLook.of(grid(sky), w, 5, 5, 35, 45, h)!!
        val b = CardLook.of(grid(sky, shift = 1), w, 6, 5, 36, 45, h)!!
        assertFalse(CardLook.differs(a, b))
    }

    @Test fun otherArtIsAnotherCard() {
        val a = CardLook.of(grid(sky), w, 5, 5, 35, 45, h)!!
        val b = CardLook.of(grid(forest), w, 5, 5, 35, 45, h)!!
        assertTrue(CardLook.differs(a, b))
    }

    @Test fun tinyOutlineHasNoLook() {
        assertNull(CardLook.of(grid(sky), w, 5, 5, 9, 9, h))
        assertNotNull(CardLook.of(grid(sky), w, -3, -3, 60, 60, h))
    }
}
