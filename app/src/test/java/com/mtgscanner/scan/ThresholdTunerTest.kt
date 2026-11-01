package com.mtgscanner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThresholdTunerTest {
    @Test fun tooFewScans() {
        assertNull(ThresholdTuner.tune(List(5) { TuneSample(10f, 0.2f, true) }))
    }

    @Test fun loosensWhenRightAnswersAreHeldBack() {
        // Right answers that stand out less than the default asks; wrong ones stand out very little.
        val right = List(40) { TuneSample(CardGallery.MIN_STAND_OUT - 1f, 0.1f, true) }
        val wrong = List(10) { TuneSample(4f, 0.02f, false) }
        val r = ThresholdTuner.tune(right + wrong)!!
        assertEquals(40, r.rightAfter)
        assertEquals(0, r.wrongAfter)
        assertTrue(r.rightAfter > r.rightBefore)
        assertTrue(r.thresholds.standOut <= CardGallery.MIN_STAND_OUT - 1f)
    }

    @Test fun keepsDefaultsWhenNothingIsBetter() {
        val all = List(30) { TuneSample(20f, 0.5f, true) }
        val r = ThresholdTuner.tune(all)!!
        assertTrue(r.thresholds.isDefault)
        assertEquals(30, r.rightAfter)
    }
}
