package com.mtgscanner.scan

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** One printing in an art index. [embedding] is the art model's view of it, when the index was built with the model. */
data class ArtEntry(
    val hash: Long, val id: String, val name: String, val set: String, val number: String,
    val embedding: ByteArray? = null,
) {
    // Arrays compare by contents here, so entries read back from a file equal the ones written.
    override fun equals(other: Any?): Boolean = other is ArtEntry && hash == other.hash && id == other.id &&
        name == other.name && set == other.set && number == other.number && embedding.contentEquals(other.embedding)

    override fun hashCode(): Int = id.hashCode() * 31 + hash.hashCode()
}

/**
 * What the art matched, and how clearly: [distance] bits off, [runnerUp] for the closest other card.
 * [similarity] is the art model's cosine when it made the choice, else 0.
 */
data class ArtHit(
    val id: String, val name: String, val set: String, val number: String, val distance: Int, val runnerUp: Int,
    val similarity: Float = 0f,
)

/**
 * Art hashes for every card of some sets, to find a card from its artwork alone when no text can be read.
 * Hashes sit in one [LongArray] so a match is a quick scan over a few thousand longs. With art model embeddings,
 * the closest [SHORTLIST] by hash are ranked again by the model. Pure Kotlin for tests.
 */
class ArtIndex(entries: List<ArtEntry>) {
    private val hashes = LongArray(entries.size) { entries[it].hash }
    private val ids = Array(entries.size) { entries[it].id }
    private val names = Array(entries.size) { entries[it].name }
    private val sets = Array(entries.size) { entries[it].set }
    private val numbers = Array(entries.size) { entries[it].number }
    private val embeddings = Array(entries.size) { entries[it].embedding }

    val size: Int get() = hashes.size
    fun hasSet(code: String): Boolean = sets.any { it.equals(code, ignoreCase = true) }

    /**
     * The card whose art is closest to any of [targets] (the same picture hashed a few ways up), when it's a
     * clear answer: within [maxDistance] bits and at least [minLead] bits closer than any card with another name.
     * Printings of the same card don't compete, as any of them is the right card. [onlySet] limits it to one set.
     *
     * With [targetEmbeddings] (the same pictures through the art model), the [SHORTLIST] closest by hash are
     * ranked by the model instead, which tells art apart better; when the model isn't sure, the hash decides.
     */
    fun match(
        targets: LongArray, onlySet: String? = null, maxDistance: Int = MAX_DISTANCE, minLead: Int = MIN_LEAD,
        targetEmbeddings: List<ByteArray> = emptyList(),
    ): ArtHit? {
        val usable = targets.filter { useful(it) }
        if (usable.isEmpty() || hashes.isEmpty()) return null
        val set = onlySet?.lowercase()
        var best = -1
        var bestD = Int.MAX_VALUE
        val dist = IntArray(hashes.size)
        for (i in hashes.indices) {
            if (set != null && sets[i] != set) { dist[i] = Int.MAX_VALUE; continue }
            var d = Int.MAX_VALUE
            for (t in usable) {
                val x = java.lang.Long.bitCount(hashes[i] xor t)
                if (x < d) d = x
            }
            dist[i] = d
            if (d < bestD) { bestD = d; best = i }
        }
        if (best < 0) return null
        if (targetEmbeddings.isNotEmpty()) rerank(dist, targetEmbeddings)?.let { return it }
        if (bestD > maxDistance) return null
        val name = names[best]
        var runnerUp = Int.MAX_VALUE
        for (i in hashes.indices) if (dist[i] < runnerUp && names[i] != name) runnerUp = dist[i]
        if (runnerUp != Int.MAX_VALUE && runnerUp - bestD < minLead) return null
        return ArtHit(ids[best], name, sets[best], numbers[best], bestD, runnerUp)
    }

    /** The art model's pick among the closest [SHORTLIST] by hash, when it's clear. */
    private fun rerank(dist: IntArray, targets: List<ByteArray>): ArtHit? {
        val short = shortlist(dist, SHORTLIST).filter { embeddings[it] != null }
        if (short.isEmpty()) return null
        val pick = ArtEmbedding.pickBest(targets, short.map { it to embeddings[it]!! }, group = { names[it] }) ?: return null
        val name = names[pick]
        val runnerUp = short.filter { names[it] != name }.minOfOrNull { dist[it] } ?: Int.MAX_VALUE
        return ArtHit(ids[pick], name, sets[pick], numbers[pick], dist[pick], runnerUp, ArtEmbedding.best(embeddings[pick]!!, targets))
    }

    companion object {
        /** Bits of 64 a camera picture may differ by and still be the same art (stricter than telling printings apart). */
        const val MAX_DISTANCE = 10
        /** How much closer than the next card the best one must be. */
        const val MIN_LEAD = 4
        /** Cards the hash shortlists for the art model to rank. */
        const val SHORTLIST = 50

        /** A nearly flat picture (black, white, glare) gives almost all-0 or all-1 bits and matches anything. */
        fun useful(hash: Long): Boolean = java.lang.Long.bitCount(hash) in 8..56

        /** Indexes of the [n] smallest distances (Int.MAX_VALUE left out), closest first. */
        fun shortlist(dist: IntArray, n: Int): List<Int> =
            dist.indices.filter { dist[it] != Int.MAX_VALUE }.sortedBy { dist[it] }.take(n)
    }
}

/** Agreement over frames: the same card must match on [needed] frames in a row before it counts. */
class ArtVote(private val needed: Int = 2) {
    private var last: String? = null
    private var count = 0

    /** Adds one frame's match (null for none). Returns the card once enough frames agree, else null. */
    fun offer(id: String?): String? {
        if (id == null) { reset(); return null }
        if (id == last) count++ else { last = id; count = 1 }
        return if (count >= needed) id else null
    }

    fun reset() {
        last = null
        count = 0
    }
}

/** What an art index file holds, from its header: the set, how many cards, and whether it has art model embeddings. */
data class ArtIndexHeader(val set: String, val cards: Int, val embedded: Boolean)

/**
 * The art index file: a header, then each card's hash, id, name and number. One file per set.
 * Version 2 adds a flag to the header and each card's art model embedding (length, then bytes; 0 for none).
 * Version 1 files are still read; they're rebuilt once the art model is here.
 */
object ArtIndexCodec {
    private const val MAGIC_V1 = 0x47414931 // "GAI1"
    private const val MAGIC_V2 = 0x47414932 // "GAI2"

    fun write(set: String, entries: List<ArtEntry>, out: OutputStream) {
        val d = DataOutputStream(out.buffered())
        d.writeInt(MAGIC_V2)
        d.writeUTF(set.lowercase())
        d.writeInt(entries.size)
        d.writeBoolean(entries.isNotEmpty() && entries.all { it.embedding != null })
        for (e in entries) {
            d.writeLong(e.hash)
            d.writeUTF(e.id)
            d.writeUTF(e.name)
            d.writeUTF(e.number)
            val emb = e.embedding
            d.writeShort(emb?.size ?: 0)
            if (emb != null) d.write(emb)
        }
        d.flush()
    }

    /** The set code and its cards. Throws [IOException] for a file that isn't an art index. */
    fun read(input: InputStream): Pair<String, List<ArtEntry>> {
        val d = DataInputStream(input.buffered())
        val v2 = when (d.readInt()) {
            MAGIC_V1 -> false
            MAGIC_V2 -> true
            else -> throw IOException("Not an art index")
        }
        val set = d.readUTF()
        val n = d.readInt()
        if (n < 0 || n > 100_000) throw IOException("Bad art index")
        if (v2) d.readBoolean()
        val list = ArrayList<ArtEntry>(n)
        repeat(n) {
            val hash = d.readLong()
            val id = d.readUTF()
            val name = d.readUTF()
            val number = d.readUTF()
            val emb = if (v2) {
                val len = d.readUnsignedShort()
                if (len > 8192) throw IOException("Bad art index")
                if (len == 0) null else ByteArray(len).also { d.readFully(it) }
            } else null
            list += ArtEntry(hash, id, name, set, number, emb)
        }
        return set to list
    }

    /** Just the set code and card count, without reading the cards. */
    fun header(input: InputStream): Pair<String, Int> = info(input).let { it.set to it.cards }

    /** The header, without reading the cards. */
    fun info(input: InputStream): ArtIndexHeader {
        val d = DataInputStream(input)
        val v2 = when (d.readInt()) {
            MAGIC_V1 -> false
            MAGIC_V2 -> true
            else -> throw IOException("Not an art index")
        }
        val set = d.readUTF()
        val n = d.readInt()
        val embedded = v2 && d.readBoolean()
        return ArtIndexHeader(set, n, embedded)
    }
}
