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
    /** The art's second hash ([ImageHash.pHash]); 0 when the index was built before there was one. */
    val phash: Long = 0L,
) {
    // Arrays compare by contents here, so entries read back from a file equal the ones written.
    override fun equals(other: Any?): Boolean = other is ArtEntry && hash == other.hash && id == other.id &&
        name == other.name && set == other.set && number == other.number && embedding.contentEquals(other.embedding) &&
        phash == other.phash

    override fun hashCode(): Int = id.hashCode() * 31 + hash.hashCode()
}

/**
 * What the art matched, and how clearly: [distance] bits off, [runnerUp] for the closest other card.
 * [similarity] is the art model's cosine when it made the choice, else 0.
 */
data class ArtHit(
    val id: String, val name: String, val set: String, val number: String, val distance: Int, val runnerUp: Int,
    val similarity: Float = 0f,
    /** For a card picture model match: how far it stood out ([StandOut]); 0 for art index matches. */
    val standOut: Float = 0f,
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
    private val phashes = LongArray(entries.size) { entries[it].phash }

    /** Set codes held, lowercase, so [hasSet] doesn't scan every entry. */
    private val setCodes: Set<String> = sets.mapTo(HashSet()) { it.lowercase() }

    val size: Int get() = hashes.size
    fun hasSet(code: String): Boolean = code.lowercase() in setCodes

    /** Where each printing's first picture sits, by Scryfall id. */
    private val byId: Map<String, Int> by lazy {
        HashMap<String, Int>(ids.size * 2).also { m -> for (i in ids.indices) m.putIfAbsent(ids[i], i) }
    }

    /** The art hash and embedding (null when the index has none) of a printing, when this index holds it. */
    fun artOf(id: String): Pair<Long, ByteArray?>? = byId[id]?.let { hashes[it] to embeddings[it] }

    /**
     * The card whose art is closest to any of [targets] (the same picture hashed a few ways up), when it's a
     * clear answer: within [maxDistance] bits and at least [minLead] bits closer than any card with another name.
     * Printings of the same card don't compete, as any of them is the right card. [onlySet] limits it to one set.
     *
     * With [targetEmbeddings] (the same pictures through the art model), the [SHORTLIST] closest by hash are
     * ranked by the model instead, which tells art apart better; when the model isn't sure, the hash decides.
     * The entries' embeddings must come from the same [model] as [targetEmbeddings] (the store drops any that don't).
     *
     * With [pTargets] (the same pictures' second hashes), the pick's second hash must agree too. And with enough
     * cards to judge by, the pick must stand out from the crowd ([StandOut]): in dim light every distance grows,
     * so a close-looking pick that doesn't stand clear is turned down, and a further one that stands far clear
     * and agrees on both hashes is taken.
     */
    fun match(
        targets: LongArray, onlySet: String? = null, maxDistance: Int = MAX_DISTANCE, minLead: Int = MIN_LEAD,
        targetEmbeddings: List<ByteArray> = emptyList(), model: ArtModel = ArtModel.STANDARD,
        pTargets: LongArray = LongArray(0),
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
        // The closest by hash has no embedding (its set isn't built with this model yet): the hash decides.
        if (targetEmbeddings.isNotEmpty() && embeddings[best] != null) rerank(dist, targetEmbeddings, model)?.let { return it }
        val pd = if (pTargets.isNotEmpty() && phashes[best] != 0L) pTargets.minOf { java.lang.Long.bitCount(phashes[best] xor it) } else null
        if (pd != null && pd > P_MAX) return null
        val counted = dist.count { it != Int.MAX_VALUE }
        val z = if (counted >= MIN_FOR_STAND_OUT) StandOut.zBelow(bestD, dist) else null
        val ok = when {
            bestD <= maxDistance -> z == null || z >= MIN_Z
            bestD <= maxDistance + STRETCH -> z != null && z >= STRONG_Z && pd != null
            else -> false
        }
        if (!ok) return null
        val name = names[best]
        var runnerUp = Int.MAX_VALUE
        for (i in hashes.indices) if (dist[i] < runnerUp && names[i] != name) runnerUp = dist[i]
        if (runnerUp != Int.MAX_VALUE && runnerUp - bestD < minLead) return null
        return ArtHit(ids[best], name, sets[best], numbers[best], bestD, runnerUp)
    }

    /** The art model's pick among the closest [SHORTLIST] by hash, when it's clear. */
    private fun rerank(dist: IntArray, targets: List<ByteArray>, model: ArtModel): ArtHit? {
        val short = shortlist(dist, SHORTLIST).filter { embeddings[it] != null }
        if (short.isEmpty()) return null
        val pick = ArtEmbedding.pickBest(
            targets, short.map { it to embeddings[it]!! }, minCos = model.minCos, minLead = model.minLead, group = { names[it] },
        ) ?: return null
        val name = names[pick]
        val runnerUp = short.filter { names[it] != name }.minOfOrNull { dist[it] } ?: Int.MAX_VALUE
        return ArtHit(ids[pick], name, sets[pick], numbers[pick], dist[pick], runnerUp, ArtEmbedding.best(embeddings[pick]!!, targets))
    }

    companion object {
        /** Bits of 64 a camera picture may differ by and still be the same art (stricter than telling printings apart). */
        const val MAX_DISTANCE = 10
        /** How much closer than the next card the best one must be. */
        const val MIN_LEAD = 4
        /** Second-hash bits of 64 a camera picture may differ by (different pictures differ by about 32). */
        const val P_MAX = 16
        /** Cards needed before judging how far a pick stands out. */
        const val MIN_FOR_STAND_OUT = 30
        /** Standard deviations a pick within [MAX_DISTANCE] must stand clear by. */
        const val MIN_Z = 3f
        /** A pick up to this many bits past [MAX_DISTANCE] is taken when it stands [STRONG_Z] clear and both hashes agree. */
        const val STRETCH = 4
        const val STRONG_Z = 5f
        /** Cards the hash shortlists for the art model to rank. */
        const val SHORTLIST = 50

        /** A nearly flat picture (black, white, glare) gives almost all-0 or all-1 bits and matches anything. */
        fun useful(hash: Long): Boolean = java.lang.Long.bitCount(hash) in 8..56

        /**
         * Indexes of the [n] smallest distances (Int.MAX_VALUE left out), closest first; ties keep index order.
         * Keeps the n best in a heap instead of sorting every entry.
         */
        fun shortlist(dist: IntArray, n: Int): List<Int> {
            if (n <= 0) return emptyList()
            // Worst on top: higher distance, then higher index.
            val heap = java.util.PriorityQueue<Int>(n + 1) { a, b -> if (dist[a] != dist[b]) dist[b].compareTo(dist[a]) else b.compareTo(a) }
            for (i in dist.indices) {
                val d = dist[i]
                if (d == Int.MAX_VALUE) continue
                if (heap.size < n) heap.add(i)
                else if (d < dist[heap.peek()]) { heap.poll(); heap.add(i) }
            }
            return heap.sortedWith(compareBy<Int>({ dist[it] }, { it }))
        }
    }
}

/** Agreement over frames: the same card must match on [needed] frames in a row before it counts. */
class ArtVote(private val needed: Int = 2, private val window: Int = 4) {
    private val recent = ArrayDeque<String?>()

    /**
     * Adds one frame's match (null for none). Returns the card once it has [needed] of the last [window] frames
     * and more than any other card, else null. A frame with no match is a lost vote, not a reset: one glared frame
     * between two good ones doesn't start the count over.
     */
    fun offer(id: String?): String? {
        recent.addLast(id)
        while (recent.size > window) recent.removeFirst()
        if (id == null) return null
        val votes = recent.filterNotNull().groupingBy { it }.eachCount()
        val mine = votes[id] ?: 0
        return if (mine >= needed && votes.none { (k, v) -> k != id && v >= mine }) id else null
    }

    fun reset() = recent.clear()
}

/**
 * What an art index file holds, from its header: the set, how many cards, whether it has art model embeddings,
 * and the id of the [ArtModel] that made them (null when it has none).
 */
data class ArtIndexHeader(val set: String, val cards: Int, val embedded: Boolean, val model: String? = null)

/** A whole art index file. */
class ArtIndexFile(val set: String, val model: String?, val entries: List<ArtEntry>)

/**
 * The art index file: a header, then each card's hash, id, name and number. One file per set.
 * Version 2 adds a flag to the header and each card's art model embedding (length, then bytes; 0 for none).
 * Version 3 adds the id of the art model that made the embeddings, after the flag. Version 4 adds each card's
 * second art hash after its first. Older files are still read
 * (version 2 embeddings are the standard model's); they're rebuilt once the chosen art model is here.
 */
object ArtIndexCodec {
    private const val MAGIC_V1 = 0x47414931 // "GAI1"
    private const val MAGIC_V2 = 0x47414932 // "GAI2"
    private const val MAGIC_V3 = 0x47414933 // "GAI3"
    private const val MAGIC_V4 = 0x47414934 // "GAI4"

    /** Writes [entries]; [model] is the id of the art model their embeddings came from. */
    fun write(set: String, entries: List<ArtEntry>, out: OutputStream, model: String? = null) {
        val d = DataOutputStream(out.buffered())
        val embedded = entries.isNotEmpty() && entries.all { it.embedding != null }
        d.writeInt(MAGIC_V4)
        d.writeUTF(set.lowercase())
        d.writeInt(entries.size)
        d.writeBoolean(embedded)
        d.writeUTF(if (embedded) model.orEmpty() else "")
        for (e in entries) {
            d.writeLong(e.hash)
            d.writeLong(e.phash)
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
    fun read(input: InputStream): Pair<String, List<ArtEntry>> = readFile(input).let { it.set to it.entries }

    /** The file's version: 1, 2 or 3. */
    private fun version(d: DataInputStream): Int = when (d.readInt()) {
        MAGIC_V1 -> 1
        MAGIC_V2 -> 2
        MAGIC_V3 -> 3
        MAGIC_V4 -> 4
        else -> throw IOException("Not an art index")
    }

    /** The model id a header names: version 2 embeddings came from the only model there was then. */
    private fun modelOf(version: Int, embedded: Boolean, stored: String?): String? = when {
        !embedded -> null
        version == 2 -> ArtModel.LEGACY.id
        else -> stored?.takeIf { it.isNotEmpty() }
    }

    /** The set, the model its embeddings came from, and its cards. Throws [IOException] for a file that isn't an art index. */
    fun readFile(input: InputStream): ArtIndexFile {
        val d = DataInputStream(input.buffered())
        val version = version(d)
        val v2 = version >= 2
        val set = d.readUTF()
        val n = d.readInt()
        if (n < 0 || n > 100_000) throw IOException("Bad art index")
        val embedded = v2 && d.readBoolean()
        val stored = if (version >= 3) d.readUTF() else null
        val list = ArrayList<ArtEntry>(n)
        repeat(n) {
            val hash = d.readLong()
            val phash = if (version >= 4) d.readLong() else 0L
            val id = d.readUTF()
            val name = d.readUTF()
            val number = d.readUTF()
            val emb = if (v2) {
                val len = d.readUnsignedShort()
                if (len > 8192) throw IOException("Bad art index")
                if (len == 0) null else ByteArray(len).also { d.readFully(it) }
            } else null
            list += ArtEntry(hash, id, name, set, number, emb, phash)
        }
        return ArtIndexFile(set, modelOf(version, embedded, stored), list)
    }

    /** Just the set code and card count, without reading the cards. */
    fun header(input: InputStream): Pair<String, Int> = info(input).let { it.set to it.cards }

    /** The header, without reading the cards. */
    fun info(input: InputStream): ArtIndexHeader {
        val d = DataInputStream(input)
        val version = version(d)
        val set = d.readUTF()
        val n = d.readInt()
        val embedded = version >= 2 && d.readBoolean()
        val stored = if (version >= 3) d.readUTF() else null
        return ArtIndexHeader(set, n, embedded, modelOf(version, embedded, stored))
    }
}
