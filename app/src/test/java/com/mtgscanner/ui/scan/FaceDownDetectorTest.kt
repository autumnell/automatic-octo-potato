package com.mtgscanner.ui.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FaceDownDetectorTest {
    @Test fun pausesAfterASecondFaceDownAndResumesWhenLifted() {
        val d = FaceDownDetector()
        assertNull(d.feed(-9.6f, 0))
        assertNull(d.feed(-9.6f, 500))
        assertEquals(true, d.feed(-9.7f, 1000))
        assertNull(d.feed(-9.7f, 1500))
        assertNull(d.feed(-7f, 1600)) // still close to flat
        assertEquals(false, d.feed(-2f, 1700))
        assertNull(d.feed(5f, 1800))
    }

    @Test fun briefFlipDoesNothing() {
        val d = FaceDownDetector()
        assertNull(d.feed(-9.6f, 0))
        assertNull(d.feed(-9.6f, 800))
        assertNull(d.feed(3f, 900))
        assertNull(d.feed(-9.6f, 1100))
        assertNull(d.feed(-9.6f, 2000))
        assertEquals(true, d.feed(-9.6f, 2100))
    }

    @Test fun faceUpNeverPauses() {
        val d = FaceDownDetector()
        for (t in 0L..5000L step 100) assertNull(d.feed(9.8f, t))
    }
}
