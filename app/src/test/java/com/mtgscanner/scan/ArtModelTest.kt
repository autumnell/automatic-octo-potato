package com.mtgscanner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class ArtModelTest {
    private fun vec(seed: Int, n: Int = 64) = Random(seed).let { r -> FloatArray(n) { r.nextFloat() * 2 - 1 } }
    private fun near(v: FloatArray, amount: Float) = Random(99).let { r -> FloatArray(v.size) { v[it] + (r.nextFloat() * 2 - 1) * amount } }
    private fun entry(hash: Long, name: String, emb: FloatArray?) =
        ArtEntry(hash, "$name-m10", name, "m10", "1", emb?.let { ArtEmbedding.quantize(it) })

    @Test fun modelsAndSizes() {
        assertEquals("4 MB", ArtModel.STANDARD.sizeLabel)
        assertEquals("11 MB", ArtModel.ADVANCED.sizeLabel)
        assertEquals(ArtModel.ADVANCED, ArtModel.byId("mnv3l-1"))
        assertNull(ArtModel.byId("nope"))
        assertTrue(ArtModel.entries.all { it.url.endsWith("/1/" + it.fileName) })
        assertEquals(ArtModel.STANDARD.minCos, ArtEmbedding.MIN_COS)
    }

    @Test fun indexFilesNameTheirModel() {
        val entries = listOf(entry(5L, "Lightning Bolt", vec(1)), entry(-7L, "Shock", vec(2)))
        val out = ByteArrayOutputStream()
        ArtIndexCodec.write("M10", entries, out, model = ArtModel.ADVANCED.id)
        val file = ArtIndexCodec.readFile(ByteArrayInputStream(out.toByteArray()))
        assertEquals("m10", file.set)
        assertEquals("mnv3l-1", file.model)
        assertEquals(entries, file.entries)
        assertEquals(ArtIndexHeader("m10", 2, true, "mnv3l-1"), ArtIndexCodec.info(ByteArrayInputStream(out.toByteArray())))
        // Hashes only: no model.
        val plain = ByteArrayOutputStream()
        ArtIndexCodec.write("m10", listOf(entry(5L, "Bolt", null)), plain, model = ArtModel.ADVANCED.id)
        assertEquals(ArtIndexHeader("m10", 1, false, null), ArtIndexCodec.info(ByteArrayInputStream(plain.toByteArray())))
    }

    @Test fun versionTwoFilesHoldTheStandardModel() {
        val out = ByteArrayOutputStream()
        java.io.DataOutputStream(out).apply {
            writeInt(0x47414932); writeUTF("m10"); writeInt(1); writeBoolean(true)
            writeLong(42L); writeUTF("id"); writeUTF("Shock"); writeUTF("1")
            writeShort(2); write(byteArrayOf(3, 4))
            flush()
        }
        val file = ArtIndexCodec.readFile(ByteArrayInputStream(out.toByteArray()))
        assertEquals(ArtModel.STANDARD.id, file.model)
        assertEquals(2, file.entries.single().embedding?.size)
        assertEquals(ArtIndexHeader("m10", 1, true, ArtModel.STANDARD.id), ArtIndexCodec.info(ByteArrayInputStream(out.toByteArray())))
    }

    @Test fun hashDecidesWhenTheClosestHasNoUsableEmbedding() {
        val bolt = vec(1)
        val shock = vec(2)
        // Shock is closest by hash but its set was built with another model (embedding dropped): the model's view of
        // the rest mustn't outvote it.
        val idx = ArtIndex(listOf(entry(0x0F0F_0F0F_0F0F_0F0FL, "Lightning Bolt", bolt), entry(0x0F0F_0F0F_0F0F_0F00L, "Shock", null)))
        val hit = idx.match(
            longArrayOf(0x0F0F_0F0F_0F0F_0F00L), minLead = 0,
            targetEmbeddings = listOf(ArtEmbedding.quantize(near(bolt, 0.2f))), model = ArtModel.ADVANCED,
        )
        assertEquals("Shock", hit?.name)
        assertEquals(0f, hit!!.similarity)
        // With both embedded, the model reranks as before.
        val both = ArtIndex(listOf(entry(0x0F0F_0F0F_0F0F_0F0FL, "Lightning Bolt", bolt), entry(0x0F0F_0F0F_0F0F_0F00L, "Shock", shock)))
        val reranked = both.match(
            longArrayOf(0x0F0F_0F0F_0F0F_0F01L), targetEmbeddings = listOf(ArtEmbedding.quantize(near(bolt, 0.2f))), model = ArtModel.ADVANCED,
        )
        assertEquals("Lightning Bolt", reranked?.name)
    }
}
