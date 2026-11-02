package com.mtgscanner.scan

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class CardGalleryTest {
    private val rnd = Random(7)

    private fun randomUnit(): FloatArray = CardGallery.unit(FloatArray(CardGallery.DIM) { rnd.nextFloat() * 2 - 1 })

    /** [v] with noise mixed in, at [noise] of its strength. */
    private fun noisy(v: FloatArray, noise: Float): FloatArray =
        CardGallery.unit(FloatArray(v.size) { v[it] + (rnd.nextFloat() * 2 - 1) * noise * 0.12f })

    private fun gallery(n: Int): Pair<CardGallery, List<FloatArray>> {
        val vs = List(n) { randomUnit() }
        return CardGallery.of(vs.mapIndexed { i, v -> CardGallery.Row("id$i", "Card $i", "set${i % 5}", "$i", v) }) to vs
    }

    @Test fun findsTheCardFromANoisyPicture() {
        val (g, vs) = gallery(2000)
        val hit = g.match(noisy(vs[123], 0.8f))
        assertNotNull(hit)
        assertEquals("id123", hit!!.id)
        assertTrue(hit.standOut > CardGallery.MIN_STAND_OUT)
    }

    @Test fun aWeakPictureStillConfirmsTheNameRead() {
        val (g, vs) = gallery(2000)
        // Mostly another picture: too weak to decide alone, but its best card is still the right one.
        val other = randomUnit()
        val q = CardGallery.unit(FloatArray(CardGallery.DIM) { 0.44f * vs[42][it] + 0.94f * other[it] })
        assertNull(g.match(q))
        assertEquals("id42", g.agreesWith(q, listOf("Card 42"))?.id)
        // OCR slipped a letter.
        assertEquals("id42", g.agreesWith(q, listOf("Card 4Z"))?.id)
        assertNull(g.agreesWith(q, listOf("Card 77")))
        assertNull(g.agreesWith(q, emptyList()))
    }

    @Test fun somethingElseMatchesNothing() {
        val (g, _) = gallery(2000)
        assertNull(g.match(randomUnit()))
    }

    @Test fun setLockLimitsTheSearch() {
        val (g, vs) = gallery(500)
        assertNull(g.match(noisy(vs[12], 0.3f), onlySet = "set1"))
        assertEquals("id12", g.match(noisy(vs[12], 0.3f), onlySet = "SET2")?.id)
    }

    @Test fun reprintsOfOneCardDontBlockAMatch() {
        val art = randomUnit()
        val rows = List(300) { CardGallery.Row("x$it", "Other $it", "abc", "$it", randomUnit()) } +
            CardGallery.Row("bolt-a", "Lightning Bolt", "m10", "146", art) +
            CardGallery.Row("bolt-b", "Lightning Bolt", "2xm", "141", noisy(art, 0.05f))
        val hit = CardGallery.of(rows).match(noisy(art, 0.5f))
        assertEquals("Lightning Bolt", hit?.name)
    }

    @Test fun picksThePrintingAndGivesUpOnTheSamePicture() {
        val a = randomUnit()
        val b = randomUnit()
        val rows = listOf(
            CardGallery.Row("p1", "Forest", "lea", "1", a),
            CardGallery.Row("p2", "Forest", "m10", "2", b),
            CardGallery.Row("p3", "Forest", "m11", "3", noisy(b, 0.02f)),
        )
        val g = CardGallery.of(rows)
        assertEquals("p1", g.pick(noisy(a, 0.4f), listOf("p1", "p2")))
        // p2 and p3 share a picture: either is the answer, the closer one given.
        assertTrue(g.pick(noisy(b, 0.3f), listOf("p1", "p2", "p3")) in setOf("p2", "p3"))
        // A printing the gallery lacks might be the one: no pick.
        assertNull(g.pick(noisy(a, 0.4f), listOf("p1", "p2", "missing")))
    }

    @Test fun fileRoundTripAndMerge() {
        val (g, vs) = gallery(50)
        val bytes = ByteArrayOutputStream().also { CardGallery.Codec.write(g, it) }.toByteArray()
        val back = CardGallery.Codec.read(ByteArrayInputStream(bytes))
        assertEquals(50, back.size)
        assertEquals(g.score(vs[7], 7), back.score(vs[7], 7), 1e-6f)
        assertEquals(7, back.rowOf("id7"))
        assertEquals(-1, back.rowOf("nope"))
        val extra = CardGallery.of(listOf(CardGallery.Row("new1", "New", "zzz", "1", randomUnit()), CardGallery.Row("id3", "Card 3", "set3", "3", vs[3])))
        val merged = CardGallery.merge(back, extra)
        assertEquals(51, merged.size)
        assertTrue(merged.has("new1") && merged.has("id3") && merged.has("id49"))
    }

    @Test fun publishedGalleryIsConverted() {
        val vs = List(3) { randomUnit() }
        val ids = """["a1","b2","c3"]"""
        val meta = """{"a1": "Lightning Bolt | m10 #146", "b2": "Fire // Ice | mh2 #290", "c3": "Say \"Hi\" | PLST #ABC-12"}"""
        val halves = ByteArrayOutputStream()
        for (v in vs) for (x in v) { val h = Half.fromFloat(x).toInt(); halves.write(h and 0xFF); halves.write((h shr 8) and 0xFF) }
        val out = ByteArrayOutputStream()
        CardGallery.Published.convert(ids.reader(), meta.reader(), ByteArrayInputStream(halves.toByteArray()), out)
        val g = CardGallery.Codec.read(ByteArrayInputStream(out.toByteArray()))
        assertEquals(3, g.size)
        assertEquals("Fire // Ice", g.nameAt(1))
        assertEquals("Say \"Hi\"", g.nameAt(2))
        assertEquals(1f, g.score(vs[1], 1), 0.01f)
        assertEquals(Triple("Lightning Bolt", "m10", "146"), CardGallery.Published.label("Lightning Bolt | M10 #146"))
    }

    @Test fun halfFloatsRoundTrip() {
        for (x in listOf(0f, 1f, -1f, 0.333f, 65504f, -0.0001f, 6.0e-8f)) {
            assertEquals(x, Half.toFloat(Half.fromFloat(x).toInt() and 0xFFFF), maxOf(1e-7f, Math_abs(x) * 1e-3f))
        }
    }

    private fun Math_abs(x: Float) = if (x < 0) -x else x

    @Test fun flatJsonReadsEscapes() {
        val got = ArrayList<String>()
        FlatJson.strings("""["a\"b", "c\\d", "é", "x\ny"]""".reader()) { got += it }
        assertEquals(listOf("a\"b", "c\\d", "é", "x\ny"), got)
    }

    @Test fun standOutMeasuresTheGap() {
        val values = intArrayOf(30, 32, 31, 33, 29, 31, 30, 4)
        assertTrue(StandOut.zBelow(4, values) > 2.5f)
        assertEquals(0f, StandOut.z(1f, 2.0, 2.0, 2), 0f)
    }

    @Test fun cornersComeBackInOrderAndScaled() {
        // Two guesses; the second scores higher. Its corners come in an odd order, as the model gives them.
        val scores = floatArrayOf(-2f, 3f)
        val k = floatArrayOf(
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            0.2f, 0.9f, 0.2f, 0.1f, 0.8f, 0.1f, 0.8f, 0.9f,
        )
        val q = CardPrintPrep.corners(scores, k, 630, 660)
        assertNotNull(q)
        assertArrayEquals(floatArrayOf(126f, 66f, 504f, 66f, 504f, 594f, 126f, 594f), q!!, 0.01f)
        // Too unsure, or not card-shaped.
        assertNull(CardPrintPrep.corners(floatArrayOf(-3f), k.copyOfRange(8, 16), 630, 660))
        assertNull(CardPrintPrep.corners(scores, k, 630, 1600))
    }

    @Test fun cardInputIsScaledLikeImageNet() {
        val px = IntArray(CardPrintPrep.CARD_W * CardPrintPrep.CARD_H) { 0xFFFF8000.toInt() }
        val x = CardPrintPrep.cardInput(px)
        val n = px.size
        assertEquals((1f - 0.485f) / 0.229f, x[0], 1e-4f)
        assertEquals((128 / 255f - 0.456f) / 0.224f, x[n], 1e-4f)
        assertEquals((0f - 0.406f) / 0.225f, x[2 * n + 5], 1e-4f)
    }
}
