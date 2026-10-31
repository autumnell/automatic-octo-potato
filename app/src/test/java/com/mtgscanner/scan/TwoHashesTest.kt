package com.mtgscanner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class TwoHashesTest {
    /** A smooth 32x32 picture with a bright blob at ([cx], [cy]), plus a little noise. */
    private fun picture(cx: Int, cy: Int, noise: Int = 0, seed: Int = 1): IntArray {
        val r = Random(seed)
        return IntArray(1024) { i ->
            val x = i % 32; val y = i / 32
            val d = (x - cx) * (x - cx) + (y - cy) * (y - cy)
            (200 - d * 3).coerceIn(20, 230) + (if (noise > 0) r.nextInt(-noise, noise + 1) else 0)
        }
    }

    @Test fun perceptualHashKeepsSameAndSplitsDifferent() {
        val a = ImageHash.pHash(picture(10, 12))
        val aNoisy = ImageHash.pHash(picture(10, 12, noise = 6, seed = 3))
        val b = ImageHash.pHash(picture(24, 20))
        assertTrue(ImageHash.distance(a, aNoisy) <= 8)
        assertTrue(ImageHash.distance(a, b) >= 16)
    }

    @Test fun bothHashesMustAgree() {
        val cands = listOf(Triple("x", 0L, 0L), Triple("y", -1L, 0b1111L))
        // dHash alike, second hash far off: a 26-bit cap turns it down.
        assertNull(ImageHash.pickBestBoth(0L, -1L, listOf(Triple("x", 0L, 1L), Triple("y", -1L, -1L))))
        assertEquals("x", ImageHash.pickBestBoth(1L, 0L, cands))
        // No second hash from the camera: the first alone decides, as before.
        assertEquals("x", ImageHash.pickBestBoth(1L, null, cands))
    }

    private val rnd = Random(11)
    private fun entry(i: Int, h: Long, p: Long = 0L) = ArtEntry(h, "id$i", "Card $i", "m10", "$i", phash = p)

    @Test fun artIndexChecksTheSecondHash() {
        val target = 0x0F0F0F0F0F0F0F0FL
        val entries = List(60) { entry(it, rnd.nextLong(), rnd.nextLong()) } + entry(99, target xor 0b111, 0x1234L)
        val idx = ArtIndex(entries)
        assertEquals("id99", idx.match(longArrayOf(target), pTargets = longArrayOf(0x1234L xor 0b11))?.id)
        // The second hash is a different picture: no match.
        assertNull(idx.match(longArrayOf(target), pTargets = longArrayOf(0x1234L.inv())))
        // An index from before second hashes: the first one decides.
        assertEquals("id99", ArtIndex(entries.map { it.copy(phash = 0L) }).match(longArrayOf(target), pTargets = longArrayOf(5L))?.id)
    }

    @Test fun aFurtherPickIsTakenWhenItStandsFarClear() {
        // A flat picture's hash matches anything, so the target has as many 1 bits as 0 bits.
        val target = 0x00FF00FF00FF00FFL
        // Everything else is 30-34 bits away; the right card 11 (past the usual 10) but agreeing on both hashes.
        val far = generateSequence { rnd.nextLong() }.filter { java.lang.Long.bitCount(it xor target) in 30..34 }.take(200).toList()
        val entries = far.mapIndexed { i, h -> entry(i, h, rnd.nextLong()) } + entry(99, target xor 0x7FFL, 77L)
        val idx = ArtIndex(entries)
        assertEquals("id99", idx.match(longArrayOf(target), pTargets = longArrayOf(77L))?.id)
        // Without the second hash to back it, too far.
        assertNull(idx.match(longArrayOf(target)))
    }

    @Test fun artIndexFileKeepsSecondHashes() {
        val entries = listOf(entry(1, 42L, 4242L), entry(2, 7L))
        val out = ByteArrayOutputStream().also { ArtIndexCodec.write("m10", entries, it) }
        assertEquals(entries, ArtIndexCodec.read(ByteArrayInputStream(out.toByteArray())).second)
    }

    @Test fun fingerprintsAveragedOverFrames() {
        val a = CardGallery.unit(FloatArray(4) { 1f })
        val b = CardGallery.unit(floatArrayOf(1f, 1f, 1f, -1f))
        val avg = FramePool.average(listOf(a, b))
        assertNotNull(avg)
        assertEquals(1.0, avg!!.sumOf { (it * it).toDouble() }, 1e-5)
        assertTrue(avg[3] == 0f && avg[0] > 0.5f)
    }
}
