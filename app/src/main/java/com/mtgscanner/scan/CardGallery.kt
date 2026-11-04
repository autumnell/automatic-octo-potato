package com.mtgscanner.scan

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A card the picture matched in the [CardGallery], and how clearly. */
data class GalleryHit(
    val id: String, val name: String, val set: String, val number: String,
    /** Cosine similarity, 1 for the same picture. */
    val score: Float,
    /** The best score of a card with another name. */
    val runnerUp: Float,
    /** How many standard deviations the best score stands above the average of every printing's. */
    val standOut: Float,
)

/**
 * One fingerprint (256 numbers from the card picture model) for every printing, to find a card from its picture
 * alone. Kept as 8-bit numbers with a scale per row, so all 105k printings take about 27 MB. Pure Kotlin for tests.
 */
class CardGallery(
    private val ids: Array<String>,
    private val names: Array<String>,
    private val sets: Array<String>,
    private val numbers: Array<String>,
    /** [size] rows of [DIM] signed bytes. */
    private val vecs: ByteArray,
    private val scales: FloatArray,
) {
    init {
        require(names.size == ids.size && sets.size == ids.size && numbers.size == ids.size && scales.size == ids.size)
        require(vecs.size == ids.size * DIM)
    }

    val size: Int get() = ids.size

    /** Rows sorted by id, for [rowOf]. */
    private val byId: IntArray by lazy { (0 until size).sortedWith { a, b -> ids[a].compareTo(ids[b]) }.toIntArray() }

    fun rowOf(id: String): Int {
        val order = byId
        var lo = 0
        var hi = order.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val c = ids[order[mid]].compareTo(id)
            if (c == 0) return order[mid] else if (c < 0) lo = mid + 1 else hi = mid - 1
        }
        return -1
    }

    fun has(id: String): Boolean = rowOf(id) >= 0
    fun idAt(row: Int): String = ids[row]
    fun nameAt(row: Int): String = names[row]

    /** A row's fingerprint as decimal numbers. */
    fun vectorAt(row: Int): FloatArray = FloatArray(DIM) { vecs[row * DIM + it] * scales[row] }

    /** Cosine of [q] (unit length, [DIM] numbers) with [row]. */
    fun score(q: FloatArray, row: Int): Float {
        var s = 0f
        val base = row * DIM
        for (j in 0 until DIM) s += q[j] * vecs[base + j]
        return s * scales[row]
    }

    /** The last search's scores, kept for the same fingerprint asked again (the picture check, then the lookup). */
    @Volatile private var lastScores: Triple<FloatArray, String?, FloatArray>? = null

    /**
     * Every printing's score for [q] (NaN outside [set], when given). The fingerprint is turned into whole numbers
     * like the rows, so each comparison is whole-number maths, and the rows are split across the phone's cores.
     */
    fun scoresFor(q: FloatArray, set: String? = null): FloatArray {
        lastScores?.let { (lq, ls, out) -> if (lq === q && ls == set) return out }
        val qb = ByteArray(DIM)
        val qs = quantizeInto(q, qb, 0)
        val out = FloatArray(size)
        val parts = PARTS.coerceAtMost(maxOf(1, size / 4096))
        val chunk = (size + parts - 1) / parts
        fun run(from: Int, to: Int) {
            for (r in from until to) {
                if (set != null && sets[r] != set) { out[r] = Float.NaN; continue }
                val b = r * DIM
                var acc = 0
                var j = 0
                while (j < DIM) {
                    acc += qb[j] * vecs[b + j] + qb[j + 1] * vecs[b + j + 1] + qb[j + 2] * vecs[b + j + 2] + qb[j + 3] * vecs[b + j + 3]
                    j += 4
                }
                out[r] = acc * qs * scales[r]
            }
        }
        if (parts <= 1) run(0, size)
        else java.util.stream.IntStream.range(0, parts).parallel().forEach { p -> run(p * chunk, minOf(size, (p + 1) * chunk)) }
        lastScores = Triple(q, set, out)
        return out
    }

    /**
     * The best printing of each of the [k] best-scoring cards (by name) for [q], best first, each with how far it
     * stands out. Only cards within [gap] of the best are looked at.
     */
    fun topCards(q: FloatArray, k: Int = TOP_CARDS, gap: Float = TOP_GAP, onlySet: String? = null): List<GalleryHit> {
        if (size == 0 || q.size != DIM) return emptyList()
        val scores = scoresFor(q, onlySet?.lowercase())
        var bestS = -2f
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (s in scores) {
            if (s.isNaN()) continue
            sum += s; sumSq += s.toDouble() * s; n++
            if (s > bestS) bestS = s
        }
        if (n == 0) return emptyList()
        val bestRow = HashMap<String, Int>()
        for (r in 0 until size) {
            val s = scores[r]
            if (s.isNaN() || s < bestS - gap) continue
            val prev = bestRow[names[r]]
            if (prev == null || scores[prev] < s) bestRow[names[r]] = r
        }
        val rows = bestRow.values.sortedByDescending { scores[it] }.take(k)
        return rows.mapIndexed { i, r ->
            val next = rows.getOrNull(if (i == 0) 1 else 0)?.let { scores[it] } ?: -1f
            GalleryHit(ids[r], names[r], sets[r], numbers[r], scores[r], next, StandOut.z(scores[r], sum, sumSq, n))
        }
    }

    /** Cosine of two rows: printings that reuse a picture score about 1. */
    fun rowScore(a: Int, b: Int): Float {
        var s = 0
        val ba = a * DIM
        val bb = b * DIM
        for (j in 0 until DIM) s += vecs[ba + j] * vecs[bb + j]
        return s * scales[a] * scales[b]
    }

    /**
     * The card whose picture is closest to [q], when it clearly is: a good enough score, standing well above the
     * average printing (by [StandOut]), and ahead of every card with another name. Printings of the same card don't
     * compete; the best of them is given. [onlySet] limits it to one set.
     */
    fun match(
        q: FloatArray, onlySet: String? = null,
        minScore: Float = MIN_SCORE, minStandOut: Float = MIN_STAND_OUT, minLead: Float = MIN_LEAD,
    ): GalleryHit? {
        if (size == 0 || q.size != DIM) return null
        val set = onlySet?.lowercase()
        val scores = scoresFor(q, set)
        var best = -1
        var bestS = -2f
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (r in 0 until size) {
            val s = scores[r]
            if (s.isNaN()) continue
            sum += s; sumSq += s.toDouble() * s; n++
            if (s > bestS) { bestS = s; best = r }
        }
        if (best < 0) return null
        val z = StandOut.z(bestS, sum, sumSq, n)
        val name = names[best]
        var runnerUp = -1f
        for (r in 0 until size) {
            val s = scores[r]
            if (!s.isNaN() && s > runnerUp && names[r] != name) runnerUp = s
        }
        if (bestS < minScore || z < minStandOut || bestS - runnerUp < minLead) return null
        return GalleryHit(ids[best], name, sets[best], numbers[best], bestS, runnerUp, z)
    }

    /**
     * The closest card to [q] when it is one of [names] (a name read from the card), however weakly the picture
     * alone would decide. Two independent reads agreeing (the text and the picture) stand in for two frames of
     * text, so a clear card is looked up at once. Null when the picture's best card has another name.
     */
    fun agreesWith(q: FloatArray, names: List<String>): GalleryHit? {
        if (names.isEmpty()) return null
        val hit = match(q, minScore = AGREE_MIN, minStandOut = AGREE_STAND_OUT, minLead = -2f) ?: return null
        if (nameLike(hit.name, names) >= AGREE_NAME) return hit
        // A rough read (a smudged or half-read name) can still settle a near tie: of the picture's few best cards,
        // the one the read names clearly better than the others.
        val top = topCards(q).filter { it.score >= AGREE_MIN }
        if (top.size < 2) return null
        val liked = top.map { it to nameLike(it.name, names) }.sortedByDescending { it.second }
        val (card, like) = liked[0]
        return card.takeIf { like >= ROUGH_NAME && like - liked[1].second >= ROUGH_LEAD }
    }

    /** How like any of the [read] names [cardName] (or one of its faces) is, 0 to 1. */
    private fun nameLike(cardName: String, read: List<String>): Double {
        val faces = cardName.split(" // ")
        return read.maxOfOrNull { n -> faces.maxOf { CardTextParser.similarity(n, it) } } ?: 0.0
    }

    /**
     * No other printing of [id]'s card has the same picture (a reprint with the same art and frame), so a picture
     * match names this printing and not just the card.
     */
    fun onlyPicture(id: String): Boolean {
        val row = rowOf(id)
        if (row < 0) return false
        val name = names[row]
        for (r in 0 until size) if (r != row && names[r] == name && rowScore(r, row) >= SAME_PICTURE) return false
        return true
    }

    /**
     * Of [printings] (Scryfall ids of one card), the one whose picture is closest to [q], when it clearly is:
     * [minLead] ahead of every printing with another picture. Printings sharing a picture (cosine above
     * [SAME_PICTURE]) can't be told apart and don't compete; the best of them is given. Null when fewer than two
     * are in the gallery, or none is clearly best.
     */
    fun pick(q: FloatArray, printings: List<String>, minLead: Float = PICK_LEAD, minScore: Float = PICK_MIN): String? {
        val rows = printings.map { rowOf(it) }.filter { it >= 0 }.distinct()
        // A printing missing from the gallery might be the one: only decide when they're all here.
        if (rows.size < 2 || rows.size < printings.distinct().size) return null
        val scored = rows.map { it to score(q, it) }.sortedByDescending { it.second }
        val (best, bestS) = scored.first()
        if (bestS < minScore) return null
        val other = scored.drop(1).firstOrNull { (r, _) -> rowScore(best, r) < SAME_PICTURE } ?: return ids[best]
        return if (bestS - other.second >= minLead) ids[best] else null
    }

    companion object {
        const val DIM = 256
        /**
         * From tests with Scryfall pictures made blurry, dark, cropped and glared, the right card scores 0.36–0.99;
         * on real phone scans 0.40–0.78. Wrong best cards scored up to 0.43, so the score alone can't decide.
         */
        const val MIN_SCORE = 0.38f
        /**
         * On real phone scans the right card stood 5.7–10 standard deviations above the rest and led the next card
         * by 0.07 or more; wrong best cards stood 5.1–5.6 and led by 0.06 at most.
         */
        const val MIN_STAND_OUT = 5.9f
        const val MIN_LEAD = 0.07f
        /**
         * For [agreesWith]: on real phone scans the right card was the picture's best at scores from 0.35 and
         * stand outs from 5.1, even when too close to call alone.
         */
        const val AGREE_MIN = 0.3f
        const val AGREE_STAND_OUT = 4.5f
        /** How close the read name must be to the picture's card (OCR slips a letter or two). */
        const val AGREE_NAME = 0.85
        const val SAME_PICTURE = 0.95f
        /** For a rough read choosing among the picture's best cards: how many, and how close to the best they score. */
        const val TOP_CARDS = 5
        const val TOP_GAP = 0.1f
        /** The rough read must be this like the card's name, and this much more like it than the next card's. */
        const val ROUGH_NAME = 0.6
        const val ROUGH_LEAD = 0.2
        /** Searches are split into at most this many parts, one per core. */
        val PARTS = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
        /**
         * A picture match this clear is taken on its first frame, without waiting for a second. On 511 real phone
         * scans, every best card standing out this far and leading this much was the right one (about half of them).
         */
        const val SURE_STAND_OUT = 7f
        const val SURE_LEAD = 0.12f

        /** A match clear enough to take from one frame ([SURE_STAND_OUT], [SURE_LEAD]). */
        fun isSure(standOut: Float, lead: Float): Boolean = standOut >= SURE_STAND_OUT && lead >= SURE_LEAD
        const val PICK_LEAD = 0.03f
        const val PICK_MIN = 0.3f

        /** Builds a gallery from rows of float numbers (unit length). */
        fun of(rows: List<Row>): CardGallery {
            val vecs = ByteArray(rows.size * DIM)
            val scales = FloatArray(rows.size)
            rows.forEachIndexed { i, r -> scales[i] = quantizeInto(r.vector, vecs, i * DIM) }
            return CardGallery(
                rows.map { it.id }.toTypedArray(), rows.map { it.name }.toTypedArray(),
                rows.map { it.set.lowercase() }.toTypedArray(), rows.map { it.number }.toTypedArray(), vecs, scales,
            )
        }

        /** Writes [v] as signed bytes at [at]; returns the scale that turns them back. */
        fun quantizeInto(v: FloatArray, out: ByteArray, at: Int): Float {
            var m = 0f
            for (x in v) m = maxOf(m, abs(x))
            if (m == 0f) return 0f
            val k = 127f / m
            for (j in 0 until DIM) out[at + j] = (v[j] * k).roundToInt().coerceIn(-127, 127).toByte()
            return m / 127f
        }

        /** [v] scaled to length 1 (a copy); all zeros stays zeros. */
        fun unit(v: FloatArray): FloatArray {
            var s = 0.0
            for (x in v) s += x.toDouble() * x
            val n = sqrt(s).toFloat()
            return if (n == 0f) v.copyOf() else FloatArray(v.size) { v[it] / n }
        }

        /** Both galleries in one; a printing in both is taken from [b]. */
        fun merge(a: CardGallery, b: CardGallery?): CardGallery {
            if (b == null || b.size == 0) return a
            val keep = (0 until a.size).filter { !b.has(a.ids[it]) }
            val n = keep.size + b.size
            val vecs = ByteArray(n * DIM)
            val scales = FloatArray(n)
            keep.forEachIndexed { i, r ->
                System.arraycopy(a.vecs, r * DIM, vecs, i * DIM, DIM)
                scales[i] = a.scales[r]
            }
            System.arraycopy(b.vecs, 0, vecs, keep.size * DIM, b.size * DIM)
            System.arraycopy(b.scales, 0, scales, keep.size, b.size)
            fun <T> join(x: Array<T>, y: Array<T>, make: (Int) -> Array<T?>): Array<T> {
                val out = make(n)
                keep.forEachIndexed { i, r -> out[i] = x[r] }
                for (i in 0 until b.size) out[keep.size + i] = y[i]
                @Suppress("UNCHECKED_CAST")
                return out as Array<T>
            }
            return CardGallery(
                join(a.ids, b.ids) { arrayOfNulls<String>(it) }, join(a.names, b.names) { arrayOfNulls<String>(it) },
                join(a.sets, b.sets) { arrayOfNulls<String>(it) }, join(a.numbers, b.numbers) { arrayOfNulls<String>(it) },
                vecs, scales,
            )
        }
    }

    /** One printing's fingerprint. */
    class Row(val id: String, val name: String, val set: String, val number: String, val vector: FloatArray)

    /** The gallery file: "GCG1", the count, each printing's id, name, set and number, then the scales and numbers. */
    object Codec {
        private const val MAGIC = 0x47434731 // "GCG1"

        fun write(g: CardGallery, out: OutputStream) {
            val d = DataOutputStream(out.buffered(1 shl 16))
            d.writeInt(MAGIC)
            d.writeInt(g.size)
            for (i in 0 until g.size) {
                d.writeUTF(g.ids[i]); d.writeUTF(g.names[i]); d.writeUTF(g.sets[i]); d.writeUTF(g.numbers[i])
            }
            for (s in g.scales) d.writeFloat(s)
            d.write(g.vecs)
            d.flush()
        }

        fun read(input: InputStream): CardGallery {
            val d = DataInputStream(input.buffered(1 shl 16))
            if (d.readInt() != MAGIC) throw IOException("Not a card gallery")
            val n = d.readInt()
            if (n < 0 || n > 1_000_000) throw IOException("Bad card gallery")
            // Names and sets repeat (reprints, a set's cards): one copy of each.
            val pool = HashMap<String, String>()
            fun shared(s: String) = pool.getOrPut(s) { s }
            val ids = arrayOfNulls<String>(n)
            val names = arrayOfNulls<String>(n)
            val sets = arrayOfNulls<String>(n)
            val numbers = arrayOfNulls<String>(n)
            for (i in 0 until n) {
                ids[i] = d.readUTF(); names[i] = shared(d.readUTF()); sets[i] = shared(d.readUTF()); numbers[i] = d.readUTF()
            }
            val scales = FloatArray(n) { d.readFloat() }
            val vecs = ByteArray(n * DIM)
            d.readFully(vecs)
            @Suppress("UNCHECKED_CAST")
            return CardGallery(ids as Array<String>, names as Array<String>, sets as Array<String>, numbers as Array<String>, vecs, scales)
        }
    }

    /**
     * The published gallery (maxhille/mtg-gallery-creator): a JSON list of Scryfall ids, a JSON map of id to
     * "Name | set #number", and [DIM] half-precision numbers per id, in the same order.
     */
    object Published {
        fun convert(ids: Reader, meta: Reader, halves: InputStream, out: OutputStream, progress: (Int, Int) -> Unit = { _, _ -> }) {
            val idList = ArrayList<String>(110_000)
            FlatJson.strings(ids) { idList += it }
            val labels = HashMap<String, String>(idList.size * 2)
            var key: String? = null
            FlatJson.strings(meta) { s -> val k = key; if (k == null) key = s else { labels[k] = s; key = null } }
            val n = idList.size
            val vecs = ByteArray(n * DIM)
            val scales = FloatArray(n)
            val raw = ByteArray(DIM * 2)
            val row = FloatArray(DIM)
            val d = DataInputStream(halves.buffered(1 shl 16))
            for (i in 0 until n) {
                d.readFully(raw)
                for (j in 0 until DIM) {
                    // Little-endian half floats.
                    val h = (raw[j * 2].toInt() and 0xFF) or ((raw[j * 2 + 1].toInt() and 0xFF) shl 8)
                    row[j] = Half.toFloat(h)
                }
                scales[i] = quantizeInto(row, vecs, i * DIM)
                if (i % 5000 == 0) progress(i, n)
            }
            val names = arrayOfNulls<String>(n)
            val sets = arrayOfNulls<String>(n)
            val numbers = arrayOfNulls<String>(n)
            for (i in 0 until n) {
                val (name, set, number) = label(labels[idList[i]].orEmpty())
                names[i] = name; sets[i] = set; numbers[i] = number
            }
            @Suppress("UNCHECKED_CAST")
            Codec.write(CardGallery(idList.toTypedArray(), names as Array<String>, sets as Array<String>, numbers as Array<String>, vecs, scales), out)
        }

        /** "Lightning Bolt | m10 #146" as its name, set and number. */
        fun label(s: String): Triple<String, String, String> {
            val bar = s.lastIndexOf(" | ")
            if (bar < 0) return Triple(s, "", "")
            val rest = s.substring(bar + 3)
            val hash = rest.indexOf(" #")
            return if (hash < 0) Triple(s.substring(0, bar), rest.lowercase(), "")
            else Triple(s.substring(0, bar), rest.substring(0, hash).lowercase(), rest.substring(hash + 2))
        }
    }
}

/** Half-precision (16-bit) floats. */
object Half {
    fun toFloat(h: Int): Float {
        val sign = (h ushr 15) and 1
        val exp = (h ushr 10) and 0x1F
        val mant = h and 0x3FF
        val bits = when (exp) {
            0 -> {
                if (mant == 0) sign shl 31
                else {
                    // Subnormal: normalise it.
                    var e = -1
                    var m = mant
                    do { e++; m = m shl 1 } while (m and 0x400 == 0)
                    (sign shl 31) or ((127 - 15 - e) shl 23) or ((m and 0x3FF) shl 13)
                }
            }
            31 -> (sign shl 31) or (0xFF shl 23) or (mant shl 13)
            else -> (sign shl 31) or ((exp - 15 + 127) shl 23) or (mant shl 13)
        }
        return java.lang.Float.intBitsToFloat(bits)
    }

    fun fromFloat(f: Float): Short {
        val bits = java.lang.Float.floatToIntBits(f)
        val sign = (bits ushr 16) and 0x8000
        val exp = ((bits ushr 23) and 0xFF) - 127 + 15
        val mant = bits and 0x7FFFFF
        return when {
            ((bits ushr 23) and 0xFF) == 0xFF -> (sign or 0x7C00 or (if (mant != 0) 0x200 else 0)).toShort()
            exp >= 31 -> (sign or 0x7C00).toShort()
            exp <= 0 -> {
                if (exp < -10) sign.toShort()
                else {
                    val m = mant or 0x800000
                    val shift = 14 - exp
                    var h = m shr shift
                    if ((m shr (shift - 1)) and 1 == 1) h++
                    (sign or h).toShort()
                }
            }
            else -> {
                var h = sign or (exp shl 10) or (mant shr 13)
                // Round to nearest.
                if (mant and 0x1000 != 0) h++
                h.toShort()
            }
        }
    }
}

/** Every string in a flat JSON list or map, in order, without building the whole document. */
object FlatJson {
    fun strings(input: Reader, each: (String) -> Unit) {
        val r = input.buffered(1 shl 16)
        val sb = StringBuilder()
        while (true) {
            val c = r.read()
            if (c < 0) return
            if (c != '"'.code) continue
            sb.setLength(0)
            while (true) {
                val x = r.read()
                if (x < 0) return
                if (x == '"'.code) break
                if (x == '\\'.code) {
                    when (val e = r.read()) {
                        'n'.code -> sb.append('\n')
                        't'.code -> sb.append('\t')
                        'r'.code -> sb.append('\r')
                        'b'.code -> sb.append('\b')
                        'f'.code -> sb.append('\u000C')
                        'u'.code -> {
                            val hex = CharArray(4) { r.read().toChar() }
                            sb.append(String(hex).toInt(16).toChar())
                        }
                        else -> if (e >= 0) sb.append(e.toChar())
                    }
                } else sb.append(x.toChar())
            }
            each(sb.toString())
        }
    }
}

/**
 * How far a best score stands out from all the others, in standard deviations (tmikonen's card detector). It
 * adjusts itself: in dim light or blur every score drops together, so a fixed cut-off would wrongly reject (or
 * accept), but the right card still stands well clear of the crowd.
 */
object StandOut {
    fun z(best: Float, sum: Double, sumSq: Double, n: Int): Float {
        if (n < 3) return 0f
        val mean = sum / n
        val sd = sqrt(maxOf(0.0, sumSq / n - mean * mean))
        return if (sd < 1e-9) 0f else ((best - mean) / sd).toFloat()
    }

    /** The same for values where smaller is better (bit distances): how far [best] is below the average. */
    fun zBelow(best: Int, values: IntArray, skip: Int = Int.MAX_VALUE): Float {
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (v in values) if (v != skip) { sum += v; sumSq += v.toDouble() * v; n++ }
        return z(-best.toFloat(), -sum, sumSq, n)
    }
}
