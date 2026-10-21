package com.mtgscanner.scan

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** One printing in an art index. */
data class ArtEntry(val hash: Long, val id: String, val name: String, val set: String, val number: String)

/** What the art matched, and how clearly: [distance] bits off, [runnerUp] for the closest other card. */
data class ArtHit(val id: String, val name: String, val set: String, val number: String, val distance: Int, val runnerUp: Int)

/**
 * Art hashes for every card of some sets, to find a card from its artwork alone when no text can be read.
 * Hashes sit in one [LongArray] so a match is a quick scan over a few thousand longs. Pure Kotlin for tests.
 */
class ArtIndex(entries: List<ArtEntry>) {
    private val hashes = LongArray(entries.size) { entries[it].hash }
    private val ids = Array(entries.size) { entries[it].id }
    private val names = Array(entries.size) { entries[it].name }
    private val sets = Array(entries.size) { entries[it].set }
    private val numbers = Array(entries.size) { entries[it].number }

    val size: Int get() = hashes.size
    fun hasSet(code: String): Boolean = sets.any { it.equals(code, ignoreCase = true) }

    /**
     * The card whose art is closest to any of [targets] (the same picture hashed a few ways up), when it's a
     * clear answer: within [maxDistance] bits and at least [minLead] bits closer than any card with another name.
     * Printings of the same card don't compete, as any of them is the right card. [onlySet] limits it to one set.
     */
    fun match(targets: LongArray, onlySet: String? = null, maxDistance: Int = MAX_DISTANCE, minLead: Int = MIN_LEAD): ArtHit? {
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
        if (best < 0 || bestD > maxDistance) return null
        val name = names[best]
        var runnerUp = Int.MAX_VALUE
        for (i in hashes.indices) if (dist[i] < runnerUp && names[i] != name) runnerUp = dist[i]
        if (runnerUp != Int.MAX_VALUE && runnerUp - bestD < minLead) return null
        return ArtHit(ids[best], name, sets[best], numbers[best], bestD, runnerUp)
    }

    companion object {
        /** Bits of 64 a camera picture may differ by and still be the same art (stricter than telling printings apart). */
        const val MAX_DISTANCE = 10
        /** How much closer than the next card the best one must be. */
        const val MIN_LEAD = 4

        /** A nearly flat picture (black, white, glare) gives almost all-0 or all-1 bits and matches anything. */
        fun useful(hash: Long): Boolean = java.lang.Long.bitCount(hash) in 8..56
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

/** The art index file: a header, then each card's hash, id, name and number. One file per set. */
object ArtIndexCodec {
    private const val MAGIC = 0x47414931 // "GAI1"

    fun write(set: String, entries: List<ArtEntry>, out: OutputStream) {
        val d = DataOutputStream(out.buffered())
        d.writeInt(MAGIC)
        d.writeUTF(set.lowercase())
        d.writeInt(entries.size)
        for (e in entries) {
            d.writeLong(e.hash)
            d.writeUTF(e.id)
            d.writeUTF(e.name)
            d.writeUTF(e.number)
        }
        d.flush()
    }

    /** The set code and its cards. Throws [IOException] for a file that isn't an art index. */
    fun read(input: InputStream): Pair<String, List<ArtEntry>> {
        val d = DataInputStream(input.buffered())
        if (d.readInt() != MAGIC) throw IOException("Not an art index")
        val set = d.readUTF()
        val n = d.readInt()
        if (n < 0 || n > 100_000) throw IOException("Bad art index")
        val list = ArrayList<ArtEntry>(n)
        repeat(n) {
            val hash = d.readLong()
            val id = d.readUTF()
            val name = d.readUTF()
            val number = d.readUTF()
            list += ArtEntry(hash, id, name, set, number)
        }
        return set to list
    }

    /** Just the set code and card count, without reading the cards. */
    fun header(input: InputStream): Pair<String, Int> {
        val d = DataInputStream(input)
        if (d.readInt() != MAGIC) throw IOException("Not an art index")
        return d.readUTF() to d.readInt()
    }
}
