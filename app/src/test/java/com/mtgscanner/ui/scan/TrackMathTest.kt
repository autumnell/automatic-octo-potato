package com.mtgscanner.ui.scan

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackMathTest {
    @Test
    fun uprightTurnsAPortraitPhoneFrame() {
        // A 1920x1080 frame from a phone held upright (rotation 90): the frame's top-left lands at the top-right.
        val q = floatArrayOf(0f, 0f, 1920f, 0f, 1920f, 1080f, 0f, 1080f)
        val up = TrackMath.upright(q, 1920, 1080, 90)
        assertArrayEquals(floatArrayOf(1f, 0f, 1f, 1f, 0f, 1f, 0f, 0f), up, 1e-5f)
    }

    @Test
    fun toViewCropsLikeFillCentre() {
        // A 9:16 frame on a 900x2000 view is scaled to the height; the sides are cut equally.
        val v = TrackMath.toView(floatArrayOf(0.5f, 0.5f, 0f, 0f, 1f, 1f, 0f, 1f), 9f / 16f, 900f, 2000f)
        assertEquals(450f, v[0], 0.01f)
        assertEquals(1000f, v[1], 0.01f)
        val scaledW = 2000f * 9f / 16f
        assertEquals((900f - scaledW) / 2f, v[2], 0.01f)
        assertEquals(0f, v[3], 0.01f)
    }

    @Test
    fun clockwiseStartsTopLeft() {
        // Corners in a jumbled order come back top-left, top-right, bottom-right, bottom-left.
        val p = floatArrayOf(10f, 100f, 0f, 0f, 10f, 0f, 0f, 100f)
        assertArrayEquals(floatArrayOf(0f, 0f, 10f, 0f, 10f, 100f, 0f, 100f), TrackMath.clockwise(p), 1e-5f)
    }

    @Test
    fun cardPoseOfASidewaysCardIsAQuarterTurn() {
        val upright = TrackMath.cardPose(floatArrayOf(0f, 0f, 63f, 0f, 63f, 88f, 0f, 88f))
        assertEquals(63f, upright.first, 0.01f)
        assertEquals(88f, upright.second, 0.01f)
        assertEquals(0f, upright.third, 0.01f)
        val sideways = TrackMath.cardPose(floatArrayOf(0f, 0f, 88f, 0f, 88f, 63f, 0f, 63f))
        assertEquals(63f, sideways.first, 0.01f)
        assertEquals(90f, kotlin.math.abs(sideways.third), 0.01f)
    }
}
