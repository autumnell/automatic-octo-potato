package com.mtgscanner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class CardRoundFourTest {
    private val rnd = Random(11)
    private fun randomUnit(): FloatArray = CardGallery.unit(FloatArray(CardGallery.DIM) { rnd.nextFloat() * 2 - 1 })
    private fun mix(a: FloatArray, b: FloatArray, t: Float) = CardGallery.unit(FloatArray(a.size) { a[it] * (1 - t) + b[it] * t })

    @Test fun quickScoresMatchThePlainOnes() {
        val vs = List(9000) { randomUnit() }
        val g = CardGallery.of(vs.mapIndexed { i, v -> CardGallery.Row("id$i", "Card $i", "s${i % 3}", "$i", v) })
        val q = mix(vs[42], randomUnit(), 0.3f)
        val fast = g.scoresFor(q)
        for (r in listOf(0, 42, 4500, 8999)) assertEquals(g.score(q, r), fast[r], 0.01f)
        assertTrue(g.scoresFor(q, "s1")[0].isNaN())
    }

    @Test fun aRoughReadPicksAmongNearTies() {
        // Two cards whose pictures are almost alike: the picture alone can't choose.
        val base = randomUnit()
        val a = mix(base, randomUnit(), 0.15f)
        val b = mix(base, randomUnit(), 0.15f)
        val rows = List(500) { CardGallery.Row("x$it", "Other $it", "abc", "$it", randomUnit()) } +
            CardGallery.Row("a", "Llanowar Elves", "m19", "1", a) + CardGallery.Row("b", "Elvish Mystic", "m14", "2", b)
        val g = CardGallery.of(rows)
        val q = mix(base, randomUnit(), 0.05f)
        assertNull(g.match(q))
        // "Llanxwar Elvex": too rough for the plain check, clear among the two.
        assertEquals("Llanowar Elves", g.agreesWith(q, listOf("Llanxwar Elvex"))?.name)
        // Nothing like either: no answer.
        assertNull(g.agreesWith(q, listOf("Qqqqq Zzzz")))
    }

    @Test fun adapterLearnsAShift() {
        // Photos are their card turned a little the same way each time; the correction learns to undo it.
        val shift = randomUnit()
        val cards = List(400) { randomUnit() }
        val photos = cards.map { mix(it, shift, 0.4f) }
        val ad = CardAdapter.fit(photos, cards, 1.0)
        val fixed = ad.apply(photos[5])
        var cos = 0f
        for (i in fixed.indices) cos += fixed[i] * cards[5][i]
        assertTrue("cos $cos", cos > 0.95f)
        val bytes = ByteArrayOutputStream().also { ad.write(it) }.toByteArray()
        val back = CardAdapter.read(ByteArrayInputStream(bytes))
        assertNotNull(back)
        assertEquals(fixed[3], back!!.apply(photos[5])[3], 1e-5f)
    }

    @Test fun setupsParse() {
        val s = OnnxNet.Setup(4, true)
        assertEquals(s, OnnxNet.Setup.parse(s.toString()))
        assertTrue(OnnxNet.setups(8).any { it.xnnpack })
        assertEquals(OnnxNet.Setup(2, false), OnnxNet.setups(1).first())
    }
}
