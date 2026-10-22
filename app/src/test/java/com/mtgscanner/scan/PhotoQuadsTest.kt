package com.mtgscanner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class PhotoQuadsTest {
    private fun rect(x: Float, y: Float, w: Float, h: Float) = floatArrayOf(x, y, x + w, y, x + w, y + h, x, y + h)

    /** A card at (x, y), [w] wide, with its art box and text box as OpenCV would find them. */
    private fun cardWithInsides(x: Float, y: Float, w: Float): List<FloatArray> {
        val h = w * 88f / 63f
        return listOf(
            rect(x, y, w, h),
            // Card-shaped boxes inside, as a found art box or text box can be.
            rect(x + w * 0.25f, y + h * 0.08f, w * 0.5f, w * 0.65f),
            rect(x + w * 0.3f, y + h * 0.58f, w * 0.4f, w * 0.54f),
        )
    }

    private fun xs(qs: List<FloatArray>) = qs.map { PhotoQuads.center(it).let { c -> c.first.toInt() to c.second.toInt() } }

    @Test fun binderPageGivesNineInReadingOrder() {
        val cands = ArrayList<FloatArray>()
        val w = 200f
        val h = w * 88f / 63f
        // The page's own outline is card-shaped too.
        cands += rect(0f, 0f, 3 * (w + 20), 3 * (h + 20))
        for (r in 0 until 3) for (c in 0 until 3) {
            val x = 10 + c * (w + 20)
            val y = 10 + r * (h + 20)
            // Sleeve around the card.
            cands += rect(x - 6, y - 6, w + 12, h + 12)
            cands += cardWithInsides(x, y, w)
        }
        val got = PhotoQuads.pick(cands.shuffled(java.util.Random(4)), minArea = 1000f, maxArea = 1e9f)
        assertEquals(9, got.size)
        val centers = xs(got)
        // Top row first, left to right.
        assertTrue(centers[0].first < centers[1].first && centers[1].first < centers[2].first)
        assertTrue(centers[2].second < centers[3].second)
        assertTrue(centers[3].first < centers[4].first)
    }

    @Test fun twoCardsSideBySideAreNotOneCard() {
        val w = 300f
        val h = w * 88f / 63f
        val cands = listOf(rect(0f, 0f, 2 * w + 10, h)) + cardWithInsides(0f, 0f, w) + cardWithInsides(w + 10, 0f, w)
        assertEquals(2, PhotoQuads.pick(cands, 1000f, 1e9f).size)
    }

    @Test fun oneCardWithItsInsidesIsOneCard() {
        // The insides are card-shaped, so they really are candidates.
        assertTrue(cardWithInsides(50f, 50f, 400f).all { CardOutline.cardLike(CardOutline.order(it)) })
        val got = PhotoQuads.pick(cardWithInsides(50f, 50f, 400f), 1000f, 1e9f)
        assertEquals(1, got.size)
        assertEquals(400f, got[0][2] - got[0][0], 0.5f)
    }

    @Test fun tiltedCardsAreFound() {
        fun turned(cx: Float, cy: Float, w: Float, h: Float, deg: Double): FloatArray {
            val a = Math.toRadians(deg)
            val pts = listOf(-w / 2 to -h / 2, w / 2 to -h / 2, w / 2 to h / 2, -w / 2 to h / 2)
            return pts.flatMap { (x, y) -> listOf((cx + x * cos(a) - y * sin(a)).toFloat(), (cy + x * sin(a) + y * cos(a)).toFloat()) }.toFloatArray()
        }
        val cands = listOf(turned(200f, 300f, 200f, 280f, 20.0), turned(600f, 320f, 200f, 280f, -35.0), turned(1000f, 300f, 280f, 200f, 5.0))
        assertEquals(3, PhotoQuads.pick(cands, 1000f, 1e9f).size)
    }

    @Test fun tooSmallOrTooBigIsLeftOut() {
        val cands = listOf(rect(0f, 0f, 63f, 88f), rect(100f, 0f, 630f, 880f))
        assertEquals(1, PhotoQuads.pick(cands, 10000f, 1e9f).size)
        assertEquals(0, PhotoQuads.pick(cands, 1e9f, 2e9f).size)
    }
}
