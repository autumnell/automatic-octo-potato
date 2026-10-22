package com.mtgscanner.scan

import kotlin.math.sqrt

/**
 * Picks every card among the four-cornered outlines found in a photo (a binder page, cards on a table, a
 * screenshot of a listing). Outlines nest: a sleeve around a card, the art box inside it, a row of cards that
 * happens to be card-shaped. Pure Kotlin for tests; quads are x, y pairs.
 */
object PhotoQuads {
    /** At most this many cards are read from one photo. */
    const val MAX_CARDS = 60

    /**
     * The cards among [candidates], each in [CardOutline.order]'s order, top row first, left to right.
     * [minArea] and [maxArea] bound a card's size in the photo.
     */
    fun pick(candidates: List<FloatArray>, minArea: Float, maxArea: Float): List<FloatArray> {
        val quads = candidates.asSequence()
            .filter { it.size == 8 }
            .map { CardOutline.order(it) }
            .filter { CardOutline.area(it) in minArea..maxArea && CardOutline.cardLike(it) }
            .sortedByDescending { CardOutline.area(it) }
            .toList()
        // Near copies of one outline (the hull and the outline itself) count once.
        val distinct = ArrayList<FloatArray>()
        for (q in quads) if (distinct.none { same(it, q) }) distinct += q
        // An outline around several cards (a row of two, a whole binder page) is a group, not a card. A card holds
        // at most two apart: its art box and its text box, which cover well under its whole area and hold nothing.
        fun children(q: FloatArray) = distinct.filter { it !== q && CardOutline.area(it) < CardOutline.area(q) * 0.9f && contains(q, center(it)) }
        val cards = distinct.filterNot { outer ->
            val apart = separate(children(outer))
            apart.size >= 3 || (
                apart.size == 2 &&
                    (apart.sumOf { CardOutline.area(it).toDouble() } >= CardOutline.area(outer) * 0.8 || apart.any { children(it).isNotEmpty() })
                )
        }
        // Of nested outlines keep the outer one: the card over its art box, the sleeve over the card.
        val kept = ArrayList<FloatArray>()
        for (q in cards) {
            val c = center(q)
            if (kept.none { contains(it, c) || contains(q, center(it)) }) kept += q
            if (kept.size >= MAX_CARDS) break
        }
        // An art box whose card's outline wasn't found is far smaller than the other cards.
        val typical = median(kept.map { CardOutline.area(it) })
        val sized = kept.filter { CardOutline.area(it) >= typical * 0.4f }
        return readingOrder(sized)
    }

    /** Those of [quads] that don't overlap one another (greedy, biggest first). */
    private fun separate(quads: List<FloatArray>): List<FloatArray> {
        val apart = ArrayList<FloatArray>()
        for (q in quads.sortedByDescending { CardOutline.area(it) }) {
            if (apart.none { contains(it, center(q)) || contains(q, center(it)) }) apart += q
        }
        return apart
    }

    /** Two outlines of about the same place and size. */
    private fun same(a: FloatArray, b: FloatArray): Boolean {
        val ca = center(a)
        val cb = center(b)
        val size = sqrt(maxOf(CardOutline.area(a), CardOutline.area(b)))
        val dx = ca.first - cb.first
        val dy = ca.second - cb.second
        val ratio = CardOutline.area(b) / CardOutline.area(a).coerceAtLeast(1f)
        return sqrt(dx * dx + dy * dy) < size * 0.08f && ratio in 0.92f..1.08f
    }

    fun center(q: FloatArray): Pair<Float, Float> = (q[0] + q[2] + q[4] + q[6]) / 4 to (q[1] + q[3] + q[5] + q[7]) / 4

    /** Whether point [p] lies inside the convex quad [q]. */
    fun contains(q: FloatArray, p: Pair<Float, Float>): Boolean {
        var sign = 0
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            val cross = (q[j * 2] - q[i * 2]) * (p.second - q[i * 2 + 1]) - (q[j * 2 + 1] - q[i * 2 + 1]) * (p.first - q[i * 2])
            val s = if (cross > 0) 1 else if (cross < 0) -1 else 0
            if (s == 0) continue
            if (sign == 0) sign = s else if (s != sign) return false
        }
        return true
    }

    private fun median(v: List<Float>): Float = if (v.isEmpty()) 0f else v.sorted()[v.size / 2]

    /** Rows from the top (a card whose middle is within half a card's height of the row's), each left to right. */
    fun readingOrder(quads: List<FloatArray>): List<FloatArray> {
        if (quads.size < 2) return quads
        val h = median(quads.map { q -> CardQuad.sides(q).let { maxOf(it.first, it.second) } })
        val rows = ArrayList<MutableList<FloatArray>>()
        for (q in quads.sortedBy { center(it).second }) {
            val row = rows.lastOrNull()
            if (row != null && center(q).second - center(row.first()).second < h / 2) row += q else rows += mutableListOf(q)
        }
        return rows.flatMap { r -> r.sortedBy { center(it).first } }
    }
}
