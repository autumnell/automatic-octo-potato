package com.mtgscanner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageHashTest {
    private fun gradient(seed: Int) = IntArray(72) { i -> ((i * 37 + seed * 101) % 255) }

    @Test fun sameImageHasZeroDistance() {
        val h = ImageHash.dHash(gradient(1))
        assertEquals(0, ImageHash.distance(h, ImageHash.dHash(gradient(1))))
    }

    @Test fun brightnessChangeKeepsHash() {
        val base = gradient(3)
        val brighter = IntArray(72) { (base[it] * 0.8 + 30).toInt() }
        assertEquals(ImageHash.dHash(base), ImageHash.dHash(brighter))
    }

    @Test fun picksClearWinnerOnly() {
        val target = ImageHash.dHash(gradient(5))
        val near = target xor 0b11L
        val far = target.inv()
        assertEquals("near", ImageHash.pickBest(target, listOf("near" to near, "far" to far)))
        // Two equally close candidates: not clear, so no answer.
        assertNull(ImageHash.pickBest(target, listOf("a" to (target xor 1L), "b" to (target xor 2L))))
    }

    @Test fun lumaWeightsGreenMost() {
        assertEquals(149, ImageHash.luma(0xFF00FF00.toInt()))
    }
}
