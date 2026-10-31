package com.mtgscanner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardTracksTest {
    private fun card(x: Float, y: Float, w: Float = 63f, h: Float = 88f) = floatArrayOf(x, y, x + w, y, x + w, y + h, x, y + h)

    @Test fun aCardKeepsItsTrackAsItMoves() {
        val t = CardTracks<String>()
        val first = t.update(listOf(card(0f, 0f)))[0]
        t.set(first, "Bolt")
        // Moving right a little each frame: the predicted place follows it.
        var id = first
        for (i in 1..5) id = t.update(listOf(card(i * 20f, 0f)))[0]
        assertEquals(first, id)
        assertEquals("Bolt", t.valueOf(id))
    }

    @Test fun twoCardsGetTwoTracksAndANewOneANewTrack() {
        val t = CardTracks<String>()
        val ids = t.update(listOf(card(0f, 0f), card(200f, 0f)))
        assertNotEquals(ids[0], ids[1])
        val again = t.update(listOf(card(202f, 1f), card(1f, 0f), card(400f, 0f)))
        assertEquals(ids[1], again[0])
        assertEquals(ids[0], again[1])
        assertEquals(3, t.size)
    }

    @Test fun aCardGoneForLongIsForgotten() {
        val t = CardTracks<String>(maxMissed = 1)
        val id = t.update(listOf(card(0f, 0f)))[0]
        t.set(id, "Bolt")
        t.update(emptyList())
        t.update(emptyList())
        assertNull(t.valueOf(id))
        assertNotEquals(id, t.update(listOf(card(0f, 0f)))[0])
    }

    @Test fun overlapOfBoxes() {
        assertEquals(1f, CardTracks.overlap(floatArrayOf(0f, 0f, 10f, 10f), floatArrayOf(0f, 0f, 10f, 10f)), 1e-6f)
        assertEquals(1f / 3f, CardTracks.overlap(floatArrayOf(0f, 0f, 10f, 10f), floatArrayOf(5f, 0f, 15f, 10f)), 1e-6f)
        assertEquals(0f, CardTracks.overlap(floatArrayOf(0f, 0f, 1f, 1f), floatArrayOf(2f, 2f, 3f, 3f)), 0f)
    }
}
