package com.mtgscanner.scan

/**
 * Frame-to-frame bookkeeping for the scanner that doesn't need Android. Pure Kotlin for tests.
 */

/**
 * Tells when the card in view has left. Only frames where the guide was seen empty count quickly; a frame with a
 * card but no readable text (tilted for glare) counts only after a long run, in case the guide check can't tell.
 */
class LeaveTracker(private val emptyNeeded: Int = 3, private val blankNeeded: Int = 12) {
    private var empty = 0
    private var blank = 0

    /** A frame with something read: the card is still there. */
    fun seen() { empty = 0; blank = 0 }

    /** A frame with nothing read. [guideEmpty]: the guide had no card at all. True when the card has left. */
    fun nothing(guideEmpty: Boolean): Boolean {
        if (guideEmpty) empty++ else blank++
        return empty >= emptyNeeded || blank >= blankNeeded
    }

    fun reset() { empty = 0; blank = 0 }
}

/**
 * Whether a match is the card already handled while it stays in view. The same printing always is. Another
 * printing with the same name is too (a name-only read gives the newest printing), unless both were known
 * exactly, by footer or art: then it's a different card, like Forests from several sets in a stack.
 */
fun sameCardInView(
    lastId: String?, lastOracle: String?, lastExact: Boolean,
    id: String, oracle: String?, exact: Boolean,
): Boolean {
    if (id == lastId) return true
    if (oracle == null || oracle != lastOracle) return false
    return !(exact && lastExact)
}

/**
 * The copyright year, set symbol and art of one card pooled over the frames it was in view, so one blurry frame
 * doesn't pick the printing. Cleared when another card comes into view.
 */
/**
 * Card picture fingerprints of a card with no readable text over its last frames. Each of the newest frame's
 * fingerprints (one per way up) is averaged with the most alike fingerprint of each earlier frame, so a frame's
 * glare or blur counts for less. Only alike ones pool: a fingerprint of the card turned the other way, or of
 * another card, doesn't.
 */
class PrintPool(private val keep: Int = 4) {
    private val frames = ArrayDeque<List<FloatArray>>()

    fun add(prints: List<FloatArray>) {
        if (prints.isEmpty()) return
        frames.addLast(prints)
        while (frames.size > keep) frames.removeFirst()
    }

    /** One pooled fingerprint for each of the newest frame's that is alike on two frames or more. */
    fun pooled(): List<FloatArray> {
        val newest = frames.lastOrNull() ?: return emptyList()
        return newest.mapNotNull { p ->
            val group = mutableListOf(p)
            for (f in frames) {
                if (f === newest) continue
                f.maxByOrNull { cosine(p, it) }?.takeIf { cosine(p, it) >= SAME }?.let { group += it }
            }
            if (group.size >= 2) FramePool.average(group) else null
        }
    }

    fun clear() = frames.clear()

    companion object {
        /** Fingerprints this alike (cosine) are the same card the same way up. */
        const val SAME = 0.75f

        fun cosine(a: FloatArray, b: FloatArray): Float {
            if (a.size != b.size) return -1f
            var s = 0f
            for (i in a.indices) s += a[i] * b[i]
            return s
        }
    }
}

class FramePool(private val keep: Int = 6) {
    private val years = ArrayDeque<Int>()
    private val hashes = ArrayDeque<Long>()
    private var symbol: SymbolPrint? = null
    private var embedding: ArtVector? = null
    private var phash: Long? = null
    private val prints = ArrayDeque<FloatArray>()

    val frames: Int get() = count
    private var count = 0

    fun add(g: CardTextGuess) {
        count++
        g.year?.let { push(years, it) }
        g.artHash?.let { push(hashes, it) }
        g.symbol?.let { symbol = it }
        g.artEmbedding?.let { embedding = it }
        g.artPHash?.let { phash = it }
        g.cardPrint?.let { push(prints, it) }
    }

    private fun <T> push(q: ArrayDeque<T>, v: T) {
        q.addLast(v)
        while (q.size > keep) q.removeFirst()
    }

    /** Whether the pool has art and a year: enough to pick a printing without waiting. */
    val complete: Boolean get() = years.isNotEmpty() && hashes.isNotEmpty()

    /** [g] with the pooled year (most often read), art (the hash closest to the others) and latest symbol. */
    fun pooled(g: CardTextGuess): CardTextGuess = g.copy(
        year = mostCommon(years.toList()) ?: g.year,
        artHash = medoid(hashes.toList()) ?: g.artHash,
        symbol = g.symbol ?: symbol,
        artEmbedding = g.artEmbedding ?: embedding,
        artPHash = g.artPHash ?: phash,
        // Fingerprints of several frames averaged: one frame's blur or glare counts for less.
        cardPrint = average(prints.toList()) ?: g.cardPrint,
    )

    fun clear() {
        years.clear(); hashes.clear(); symbol = null; embedding = null; phash = null; prints.clear(); count = 0
    }

    companion object {
        /** The average of [prints], scaled back to length 1; null for none. */
        fun average(prints: List<FloatArray>): FloatArray? {
            if (prints.isEmpty()) return null
            if (prints.size == 1) return prints[0]
            val sum = FloatArray(prints[0].size)
            for (p in prints) if (p.size == sum.size) for (i in sum.indices) sum[i] += p[i]
            return CardGallery.unit(sum)
        }

        /** The value seen most often; the latest wins a tie. */
        fun <T> mostCommon(values: List<T>): T? {
            if (values.isEmpty()) return null
            val counts = values.groupingBy { it }.eachCount()
            val top = counts.values.max()
            return values.last { counts[it] == top }
        }

        /** The hash with the smallest total distance to the others: a typical frame, not an outlier. */
        fun medoid(hashes: List<Long>): Long? {
            if (hashes.isEmpty()) return null
            return hashes.withIndex().minBy { (i, h) ->
                hashes.sumOf { java.lang.Long.bitCount(it xor h) } * 64 - i
            }.value
        }
    }
}
