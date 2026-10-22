package com.mtgscanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShelfLayoutTest {
    @Test
    fun placeSwapsAndRemoves() {
        var s = ShelfLayout(2, 3).place(10, 0).place(11, 4)
        assertEquals(10L, s.boxAt(0))
        s = s.place(10, 4)
        assertEquals(4, s.spotOf(10))
        assertEquals(0, s.spotOf(11))
        s = s.place(12, 4)
        assertNull(s.spotOf(10))
        assertEquals("Shelf 2 · spot 2", s.label(12))
        assertEquals(s, s.place(13, 6))
        assertNull(s.remove(12).spotOf(12))
    }

    @Test
    fun resizeKeepsRowAndColumn() {
        val s = ShelfLayout(3, 4).place(1, 5).place(2, 11).resize(3, 2)
        assertEquals(3, s.spotOf(1))
        assertNull(s.spotOf(2))
    }

    @Test
    fun encodeDecode() {
        val s = ShelfLayout(2, 5).place(4, 3).place(9, 7)
        assertEquals(s, ShelfLayout.decode(s.encode()))
        assertEquals(ShelfLayout(), ShelfLayout.decode(null))
        assertEquals(ShelfLayout(2, 2), ShelfLayout.decode("2,2;x:1,5:99"))
    }
}
