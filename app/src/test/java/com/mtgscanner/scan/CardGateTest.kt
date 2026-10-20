package com.mtgscanner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CardGateTest {
    private val w = 120
    private val h = 160

    /** A dark table with a little sensor noise. */
    private fun table(seed: Int = 1, level: Int = 40): IntArray {
        val r = Random(seed)
        return IntArray(w * h) { level + r.nextInt(-3, 4) }
    }

    /** A light card at (l, t), [cw] wide, with dark "text" lines and an art box inside. */
    private fun withCard(base: IntArray, l: Int, t: Int, cw: Int, ch: Int): IntArray {
        val px = base.copyOf()
        for (y in t until t + ch) for (x in l until l + cw) {
            val iy = y - t
            val ix = x - l
            px[y * w + x] = when {
                ix < 4 || ix >= cw - 4 || iy < 4 || iy >= ch - 4 -> 30 // black border
                iy in 12 until ch / 2 && ix in 8 until cw - 8 -> 120 // art
                iy > ch / 2 + 6 && iy % 6 < 2 && ix in 10 until cw - 20 -> 60 // rules text
                else -> 215
            }
        }
        return px
    }

    @Test fun emptyTableSkipsReading() {
        val gate = CardGate()
        assertEquals(GateState.EMPTY, gate.check(table(1), w, h).state)
        assertEquals(GateState.EMPTY, gate.check(table(2), w, h).state)
        assertFalse(gate.check(table(3), w, h).runOcr)
    }

    @Test fun steadyCardIsFoundOnTheSecondFrame() {
        val gate = CardGate()
        val frame = withCard(table(1), 15, 20, 90, 126)
        val first = gate.check(frame, w, h)
        assertEquals(GateState.SETTLING, first.state)
        assertFalse(first.runOcr)
        val second = gate.check(withCard(table(2), 15, 20, 90, 126), w, h)
        assertEquals(GateState.CARD, second.state)
        assertTrue(second.runOcr)
        val box = second.card!!
        assertTrue(kotlin.math.abs(box.left - 15) <= 5)
        assertTrue(kotlin.math.abs(box.right - 105) <= 5)
        assertTrue(kotlin.math.abs(box.top - 20) <= 5)
        assertTrue(kotlin.math.abs(box.bottom - 146) <= 5)
    }

    @Test fun movingCardWaits() {
        val gate = CardGate()
        gate.check(withCard(table(1), 5, 5, 90, 126), w, h)
        val moved = gate.check(withCard(table(2), 25, 30, 90, 126), w, h)
        assertEquals(GateState.MOVING, moved.state)
        assertFalse(moved.runOcr)
        // Settles again: one more steady frame and it reads.
        assertTrue(gate.check(withCard(table(3), 25, 30, 90, 126), w, h).runOcr)
    }

    @Test fun busyPictureWithoutOutlineStillReads() {
        // Random texture everywhere: no outline, but not empty, so it's read to be safe.
        val r = Random(7)
        val busy = IntArray(w * h) { r.nextInt(0, 256) }
        val gate = CardGate(moveLimit = 255)
        gate.check(busy, w, h)
        val res = gate.check(busy, w, h)
        assertEquals(GateState.UNSURE, res.state)
        assertTrue(res.runOcr)
    }

    @Test fun findsSidewaysCard() {
        val l = 4
        val t = 40
        assertNotNull(CardFinder.find(withCard(table(1), l, t, 112, 80), w, h))
    }

    @Test fun smallOrOddShapesAreNotCards() {
        assertNull(CardFinder.find(withCard(table(1), 50, 60, 20, 28), w, h))
        assertNull(CardFinder.find(withCard(table(1), 5, 60, 110, 30), w, h))
    }

    @Test fun guideCropIsCenteredAndCardShaped() {
        val c = FrameGeometry.guideCrop(720, 1280)
        assertEquals(720 - c.right, c.left)
        assertEquals(1280 - c.bottom, c.top)
        assertTrue(c.width in 565..575)
        assertTrue(kotlin.math.abs(c.width.toDouble() / c.height - FrameGeometry.CARD_ASPECT) < 0.01)
    }

    @Test fun sensorAndUprightBoxesRoundTrip() {
        val box = OcrBox(100, 50, 300, 400)
        for (r in listOf(0, 90, 180, 270)) {
            val sensor = FrameGeometry.toSensor(box, r, 1280, 720)
            assertEquals("rotation $r", box, FrameGeometry.toUpright(sensor, r, 1280, 720))
        }
        // Turned 90° clockwise, the upright top-left corner was the sensor's bottom-left.
        assertEquals(OcrBox(0, 720 - 10, 10, 720), FrameGeometry.toSensor(OcrBox(0, 0, 10, 10), 90, 1280, 720))
    }
}
