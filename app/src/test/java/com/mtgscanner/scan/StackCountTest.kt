package com.mtgscanner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StackCountTest {
    /** A stack seen edge on: [cards] edges [period] pixels apart, each a light band with a dark line between. */
    private fun stack(cards: Int, period: Int, w: Int = 200, pad: Int = 30, rows: Boolean = true): Triple<IntArray, Int, Int> {
        val len = cards * period + 2 * pad
        val r = Random(3)
        val img = IntArray(w * len)
        for (i in 0 until len) {
            val inStack = i in pad until pad + cards * period
            val v = when {
                !inStack -> 90
                (i - pad) % period == 0 -> 60
                else -> 200
            }
            for (x in 0 until w) img[i * w + x] = (v + r.nextInt(-8, 9)).coerceIn(0, 255)
        }
        return if (rows) Triple(img, w, len) else {
            val t = IntArray(w * len)
            for (y in 0 until len) for (x in 0 until w) t[x * len + y] = img[y * w + x]
            Triple(t, len, w)
        }
    }

    @Test fun countsEdges() {
        val (img, w, h) = stack(cards = 25, period = 8)
        val r = StackCount.count(img, w, h)!!
        assertTrue("got ${r.count}", kotlin.math.abs(r.count - 25) <= 1)
        assertTrue(r.sure)
    }

    @Test fun eitherWayRound() {
        val (img, w, h) = stack(cards = 15, period = 10, rows = false)
        val r = StackCount.count(img, w, h)!!
        assertTrue("got ${r.count}", kotlin.math.abs(r.count - 15) <= 1)
    }

    @Test fun plainPictureIsNoStack() {
        val r = StackCount.count(IntArray(200 * 200) { 128 }, 200, 200)
        assertTrue(r == null || !r.sure)
    }
}
