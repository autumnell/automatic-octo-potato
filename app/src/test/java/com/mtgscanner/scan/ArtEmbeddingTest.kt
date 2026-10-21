package com.mtgscanner.scan

import com.mtgscanner.data.PrintingPick
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class ArtEmbeddingTest {
    private fun vec(seed: Int, n: Int = 64) = Random(seed).let { r -> FloatArray(n) { r.nextFloat() * 2 - 1 } }

    /** [v] with a little noise added: the same art seen by the camera. */
    private fun near(v: FloatArray, amount: Float, seed: Int = 99) = Random(seed).let { r -> FloatArray(v.size) { v[it] + (r.nextFloat() * 2 - 1) * amount } }

    @Test fun quantizeIsUnitLengthBytes() {
        val q = ArtEmbedding.quantize(floatArrayOf(3f, 4f))
        assertArrayEquals(byteArrayOf(76, 102), q)
        assertEquals(1f, ArtEmbedding.cosine(q, q), 1e-4f)
        assertArrayEquals(ByteArray(3), ArtEmbedding.quantize(FloatArray(3)))
    }

    @Test fun cosineMatchesFloats() {
        val a = vec(1)
        val b = vec(2)
        var dot = 0.0; var na = 0.0; var nb = 0.0
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        val exact = (dot / Math.sqrt(na * nb)).toFloat()
        assertEquals(exact, ArtEmbedding.cosine(ArtEmbedding.quantize(a), ArtEmbedding.quantize(b)), 0.02f)
        assertEquals(0f, ArtEmbedding.cosine(ByteArray(4), ByteArray(4)))
        assertEquals(0f, ArtEmbedding.cosine(ByteArray(4) { 1 }, ByteArray(5) { 1 }))
    }

    @Test fun pickBestNeedsALead() {
        val a = vec(1)
        val b = vec(2)
        val cands = listOf("a" to ArtEmbedding.quantize(a), "b" to ArtEmbedding.quantize(b))
        assertEquals("a", ArtEmbedding.pickBest(listOf(ArtEmbedding.quantize(near(a, 0.2f))), cands))
        // Halfway between the two: no clear answer.
        val na = Math.sqrt(a.sumOf { it.toDouble() * it }).toFloat()
        val nb = Math.sqrt(b.sumOf { it.toDouble() * it }).toFloat()
        val mid = FloatArray(a.size) { a[it] / na + b[it] / nb }
        assertNull(ArtEmbedding.pickBest(listOf(ArtEmbedding.quantize(mid)), cands, minCos = 0.5f))
        // Unlike anything.
        assertNull(ArtEmbedding.pickBest(listOf(ArtEmbedding.quantize(vec(7))), cands))
    }

    @Test fun sameGroupDoesNotCompete() {
        val a = vec(1)
        val cands = listOf("a1" to ArtEmbedding.quantize(a), "a2" to ArtEmbedding.quantize(near(a, 0.05f, 3)), "b" to ArtEmbedding.quantize(vec(2)))
        assertEquals("a1", ArtEmbedding.pickBest(listOf(ArtEmbedding.quantize(near(a, 0.1f))), cands, group = { it.take(1) }))
    }

    private fun entry(hash: Long, name: String, emb: FloatArray?, set: String = "m10") =
        ArtEntry(hash, "$name-$set", name, set, "1", emb?.let { ArtEmbedding.quantize(it) })

    @Test fun indexReranksTheHashShortlist() {
        val bolt = vec(1)
        val shock = vec(2)
        // The hashes say Shock is closer (glare), the model says Bolt.
        val idx = ArtIndex(listOf(entry(0x0F0F_0F0F_0F0F_0F0FL, "Lightning Bolt", bolt), entry(0x0F0F_0F0F_0F0F_0F00L, "Shock", shock)))
        val target = 0x0F0F_0F0F_0F0F_0F01L
        assertNull(idx.match(longArrayOf(target)))
        val hit = idx.match(longArrayOf(target), targetEmbeddings = listOf(ArtEmbedding.quantize(near(bolt, 0.2f))))
        assertEquals("Lightning Bolt", hit?.name)
        assertTrue(hit!!.similarity > ArtEmbedding.MIN_COS)
    }

    @Test fun indexFallsBackToTheHashWhenTheModelIsUnsure() {
        val idx = ArtIndex(listOf(entry(0x0F0F_0F0F_0F0F_0F0FL, "Lightning Bolt", vec(1)), entry(0x3333_3333_3333_3333L, "Grizzly Bears", vec(2))))
        val hit = idx.match(longArrayOf(0x0F0F_0F0F_0F0F_0F0EL), targetEmbeddings = listOf(ArtEmbedding.quantize(vec(9))))
        assertEquals("Lightning Bolt", hit?.name)
        assertEquals(0f, hit!!.similarity)
        // Entries without embeddings (an old index file) use the hash alone.
        val old = ArtIndex(listOf(entry(0x0F0F_0F0F_0F0F_0F0FL, "Lightning Bolt", null)))
        assertEquals("Lightning Bolt", old.match(longArrayOf(0x0F0F_0F0F_0F0F_0F0FL), targetEmbeddings = listOf(ArtEmbedding.quantize(vec(1))))?.name)
    }

    @Test fun shortlistKeepsTheClosest() {
        assertEquals(listOf(2, 0), ArtIndex.shortlist(intArrayOf(5, Int.MAX_VALUE, 1, 9), 2))
    }

    @Test fun codecV2KeepsEmbeddings() {
        val entries = listOf(entry(5L, "Lightning Bolt", vec(1)), entry(-7L, "Shock", vec(2)))
        val out = ByteArrayOutputStream()
        ArtIndexCodec.write("M10", entries, out)
        val (set, back) = ArtIndexCodec.read(ByteArrayInputStream(out.toByteArray()))
        assertEquals("m10", set)
        assertEquals(entries, back)
        assertArrayEquals(entries[1].embedding, back[1].embedding)
        assertEquals(ArtIndexHeader("m10", 2, true), ArtIndexCodec.info(ByteArrayInputStream(out.toByteArray())))
        val plain = ByteArrayOutputStream()
        ArtIndexCodec.write("m10", listOf(entry(5L, "Bolt", null)), plain)
        assertFalse(ArtIndexCodec.info(ByteArrayInputStream(plain.toByteArray())).embedded)
    }

    @Test fun readsVersionOneFiles() {
        val out = ByteArrayOutputStream()
        java.io.DataOutputStream(out).apply {
            writeInt(0x47414931); writeUTF("lea"); writeInt(1)
            writeLong(42L); writeUTF("id"); writeUTF("Black Lotus"); writeUTF("232")
            flush()
        }
        val (_, back) = ArtIndexCodec.read(ByteArrayInputStream(out.toByteArray()))
        assertEquals(listOf(ArtEntry(42L, "id", "Black Lotus", "lea", "232")), back)
        assertEquals(ArtIndexHeader("lea", 1, false), ArtIndexCodec.info(ByteArrayInputStream(out.toByteArray())))
    }

    @Test fun printingsByYear() {
        val p = listOf("4ed" to "1995-04-01", "5ed" to "1997-03-24", "tmp" to "1997-10-14", "6ed" to "1999-04-21", "x" to null)
        // 1998: none from that year; those within a year, newest first.
        assertEquals(listOf("6ed", "tmp", "5ed"), PrintingPick.byYear(p, 1998) { it.second }.map { it.first })
        // 1997: that year's first, then the year after.
        assertEquals(listOf("tmp", "5ed"), PrintingPick.byYear(p, 1997) { it.second }.map { it.first })
        assertEquals("4ed", PrintingPick.byYear(p, 1995) { it.second }.first().first)
        assertTrue(PrintingPick.byYear(p, 2020) { it.second }.isEmpty())
        assertEquals("6ed", PrintingPick.newestFirst(p) { it.second }.first().first)
    }

    @Test fun glareNeedsABigWhitePatch() {
        val w = 120
        val h = 30
        val paper = IntArray(w * h) { 0xFFE8E4DC.toInt() }
        assertFalse(LightCheck.glare(paper, w, h))
        val glare = paper.copyOf()
        for (y in 0 until h) for (x in 20 until 50) glare[y * w + x] = 0xFFFFFFFF.toInt()
        assertTrue(LightCheck.glare(glare, w, h))
        // A few white specks are not glare.
        val specks = paper.copyOf()
        for (i in 0 until w * h step 7) specks[i] = 0xFFFFFFFF.toInt()
        assertFalse(LightCheck.glare(specks, w, h))
    }
}
