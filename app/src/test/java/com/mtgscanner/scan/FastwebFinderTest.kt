package com.mtgscanner.scan

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FastwebFinderTest {
    /** A card standing in the middle of the picture, corners given out of order as the model may. */
    private val corners = floatArrayOf(0.7f, 0.8f, 0.3f, 0.2f, 0.3f, 0.8f, 0.7f, 0.2f)

    @Test fun cornersComeBackInPictureCoordinatesTopLeftFirst() {
        val q = FastwebFinder.corners(mapOf("corners" to corners, "sharpness" to floatArrayOf(0.5f)), 1000, 500)
        assertNotNull(q)
        assertArrayEquals(floatArrayOf(300f, 100f, 700f, 100f, 700f, 400f, 300f, 400f), q, 0.01f)
    }

    @Test fun blurryOrAbsentCardGivesNothing() {
        assertNull(FastwebFinder.corners(mapOf("corners" to corners, "sharpness" to floatArrayOf(0.01f)), 100, 100))
        assertNull(FastwebFinder.corners(mapOf("corners" to corners, "presence" to floatArrayOf(-3f)), 100, 100))
        assertNotNull(FastwebFinder.corners(mapOf("corners" to corners, "presence" to floatArrayOf(3f)), 100, 100))
        assertNull(FastwebFinder.corners(mapOf("sharpness" to floatArrayOf(1f)), 100, 100))
    }

    @Test fun tinyOrCollapsedQuadsAreRefused() {
        val tiny = floatArrayOf(0.5f, 0.5f, 0.55f, 0.5f, 0.55f, 0.55f, 0.5f, 0.55f)
        assertNull(FastwebFinder.corners(mapOf("corners" to tiny), 100, 100))
        val collapsed = floatArrayOf(0.2f, 0.2f, 0.21f, 0.2f, 0.8f, 0.8f, 0.2f, 0.8f)
        assertNull(FastwebFinder.corners(mapOf("corners" to collapsed), 100, 100))
    }

    @Test fun inputIsImageNetScaledPlanes() {
        val n = FastwebFinder.SIZE * FastwebFinder.SIZE
        val white = IntArray(n) { 0xFFFFFFFF.toInt() }
        val x = FastwebFinder.input(white)
        assertEquals(3 * n, x.size)
        assertEquals((1f - 0.485f) / 0.229f, x[0], 1e-4f)
        assertEquals((1f - 0.456f) / 0.224f, x[n], 1e-4f)
        assertEquals((1f - 0.406f) / 0.225f, x[2 * n], 1e-4f)
    }
}
