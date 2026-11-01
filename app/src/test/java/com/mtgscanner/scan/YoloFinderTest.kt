package com.mtgscanner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class YoloFinderTest {
    private val anchors = 6

    /** A tiny output: rows cx, cy, w, h, score, angle; one value per guess. */
    private fun output(vararg guesses: FloatArray): FloatArray {
        val out = FloatArray(6 * anchors)
        guesses.forEachIndexed { a, g -> for (r in 0 until 6) out[r * anchors + a] = g[r] }
        return out
    }

    @Test fun straightBoxCorners() {
        val q = YoloFinder.box(100f, 100f, 40f, 60f, 0f)
        val xs = q.filterIndexed { i, _ -> i % 2 == 0 }.sorted()
        val ys = q.filterIndexed { i, _ -> i % 2 == 1 }.sorted()
        assertEquals(80f, xs.first(), 0.01f); assertEquals(120f, xs.last(), 0.01f)
        assertEquals(70f, ys.first(), 0.01f); assertEquals(130f, ys.last(), 0.01f)
    }

    @Test fun bestCardBackInThePicture() {
        // A 1280 x 960 picture is halved into the 640 square; a card 100 x 140 there, centred at (300, 200).
        val out = output(floatArrayOf(10f, 10f, 5f, 5f, 0.2f, 0f), floatArrayOf(300f, 200f, 100f, 140f, 0.9f, 0.1f))
        val q = YoloFinder.corners(out, 1280, 960, anchors = anchors)
        assertNotNull(q)
        val cx = (0 until 4).map { q!![it * 2] }.average()
        val cy = (0 until 4).map { q!![it * 2 + 1] }.average()
        assertEquals(600.0, cx, 1.0)
        assertEquals(400.0, cy, 1.0)
    }

    @Test fun nothingSureEnough() {
        assertNull(YoloFinder.corners(output(floatArrayOf(300f, 200f, 100f, 140f, 0.1f, 0f)), 640, 640, anchors = anchors))
        assertNull(YoloFinder.corners(FloatArray(7), 640, 640, anchors = anchors))
    }

    @Test fun anchorsAt640() = assertEquals(8400, YoloFinder.ANCHORS)
}
