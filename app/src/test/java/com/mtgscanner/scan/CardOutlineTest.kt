package com.mtgscanner.scan

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardOutlineTest {
    /** An upright card 630 x 880 at (100, 50), corners in CardQuad's order. */
    private val card = floatArrayOf(100f, 50f, 730f, 50f, 730f, 930f, 100f, 930f)

    @Test fun ordersCornersClockwiseFromTopLeft() {
        val shuffled = floatArrayOf(730f, 930f, 100f, 50f, 100f, 930f, 730f, 50f)
        assertArrayEquals(card, CardOutline.order(shuffled), 0f)
        // Counter-clockwise input comes out clockwise too.
        val ccw = floatArrayOf(100f, 50f, 100f, 930f, 730f, 930f, 730f, 50f)
        assertArrayEquals(card, CardOutline.order(ccw), 0f)
    }

    @Test fun ordersATiltedCard() {
        // Turned about 20 degrees: the top-left corner is still the one nearest the image's top-left.
        val q = floatArrayOf(300f, 100f, 890f, 315f, 590f, 1142f, 0f, 927f)
        val o = CardOutline.order(floatArrayOf(q[4], q[5], q[0], q[1], q[6], q[7], q[2], q[3]))
        assertArrayEquals(q, o, 0f)
        assertTrue(CardOutline.cardLike(o))
    }

    @Test fun areaAndConvexity() {
        assertEquals(630f * 880f, CardOutline.area(card), 1f)
        assertTrue(CardOutline.isConvex(card))
        // A dent: one corner pulled inside.
        assertFalse(CardOutline.isConvex(floatArrayOf(100f, 50f, 730f, 50f, 400f, 400f, 100f, 930f)))
    }

    @Test fun cardShapeEitherWayRoundAndAtAnAngle() {
        assertTrue(CardOutline.cardLike(card))
        // Lying sideways.
        assertTrue(CardOutline.cardLike(floatArrayOf(0f, 0f, 880f, 0f, 880f, 630f, 0f, 630f)))
        // Seen from below: the top edge is shorter than the bottom one.
        assertTrue(CardOutline.cardLike(floatArrayOf(160f, 80f, 670f, 80f, 730f, 930f, 100f, 930f)))
        // A square, a long strip and a sliver are not cards.
        assertFalse(CardOutline.cardLike(floatArrayOf(0f, 0f, 500f, 0f, 500f, 500f, 0f, 500f)))
        assertFalse(CardOutline.cardLike(floatArrayOf(0f, 0f, 900f, 0f, 900f, 200f, 0f, 200f)))
        assertFalse(CardOutline.cardLike(floatArrayOf(300f, 80f, 500f, 80f, 730f, 930f, 100f, 930f)))
    }

    @Test fun picksTheBiggestCardShapedOutline() {
        // The art window inside the card (wider than tall) and a smaller card-shaped text box.
        val art = floatArrayOf(150f, 150f, 680f, 150f, 680f, 520f, 150f, 520f)
        val textBox = floatArrayOf(150f, 560f, 380f, 560f, 380f, 880f, 150f, 880f)
        val table = floatArrayOf(0f, 0f, 1000f, 0f, 1000f, 1000f, 0f, 1000f)
        assertArrayEquals(card, CardOutline.pick(listOf(art, textBox, table, card), minArea = 10_000f), 0f)
        // Too small to count.
        assertNull(CardOutline.pick(listOf(textBox), minArea = 100_000f))
        assertNull(CardOutline.pick(emptyList(), minArea = 1f))
    }
}
