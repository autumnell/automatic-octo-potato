package com.mtgscanner.scan

/**
 * Matches OCR text against every Magic card name, on the phone, with no network.
 * Double-faced cards can be matched by either face.
 *
 * [extras] are token and emblem names. A card name always wins a tie with one, and an extra's score is lowered a
 * little ([EXTRA_PENALTY]) so a near miss on a real card still comes first.
 */
class CardNameMatcher(names: Collection<String>, extras: Collection<String> = emptyList()) {
    /** [extra]: a token or emblem name, not a card's. */
    data class Match(val name: String, val score: Double, val extra: Boolean = false)

    /** normalized face name -> full card name */
    private val byNorm = HashMap<String, String>(names.size * 2)
    /** normalized names grouped by length, so only similar lengths are compared */
    private val byLength = HashMap<Int, MutableList<String>>()

    /** Normalized names that only belong to tokens and emblems. */
    private val extraNorms = HashSet<String>()

    init {
        // Card names first: a token sharing a card's name ("Ornithopter") stays the card.
        for ((list, extra) in listOf(names to false, extras to true)) {
            for (full in list) {
                val faces = listOf(full) + if (" // " in full) full.split(" // ") else emptyList()
                for (face in faces) {
                    val n = CardTextParser.normalize(face)
                    if (n.isEmpty() || byNorm.containsKey(n)) continue
                    byNorm[n] = full
                    if (extra) extraNorms += n
                    byLength.getOrPut(n.length) { mutableListOf() } += n
                }
            }
        }
    }

    /** Whether [name] is only a token's or emblem's name. */
    fun isExtra(name: String): Boolean = CardTextParser.normalize(name) in extraNorms

    val size: Int get() = byNorm.size

    /** The closest card name to [text], or null when nothing is close (score below [minScore]). */
    fun best(text: String, minScore: Double = 0.72): Match? {
        val q = CardTextParser.normalize(text)
        if (q.length < 3) return null
        byNorm[q]?.let { return if (q in extraNorms) Match(it, 1.0 - EXTRA_PENALTY, extra = true) else Match(it, 1.0) }
        val slack = maxOf(2, q.length / 4)
        var bestName: String? = null
        var bestDist = Int.MAX_VALUE
        var bestLen = 1
        for (len in (q.length - slack)..(q.length + slack)) {
            val bucket = byLength[len] ?: continue
            for (n in bucket) {
                val isExtra = n in extraNorms
                // An extra needs to be strictly closer than the best card name so far; a card name wins ties.
                val limit = minOf(bestDist + if (!isExtra && bestName != null && bestName in extraNorms) 1 else 0, slack + 1)
                val d = boundedLevenshtein(q, n, limit)
                // d == limit means the search gave up (the real distance is larger), so it never counts as a match.
                val better = d < limit && (d < bestDist || (d == bestDist && !isExtra && bestName != null && bestName in extraNorms))
                if (better) {
                    bestDist = d; bestName = n; bestLen = maxOf(q.length, n.length)
                    if (d == 0) break
                }
            }
        }
        val name = bestName ?: return null
        val extra = name in extraNorms
        val score = 1.0 - bestDist.toDouble() / bestLen - if (extra) EXTRA_PENALTY else 0.0
        return if (score >= minScore) Match(byNorm.getValue(name), score, extra) else null
    }

    companion object {
        /** Taken off a token or emblem name's score. */
        const val EXTRA_PENALTY = 0.03

        /** Edit distance that gives up (returns [limit]) as soon as it can't beat [limit]. */
        fun boundedLevenshtein(a: String, b: String, limit: Int): Int {
            if (kotlin.math.abs(a.length - b.length) >= limit) return limit
            var prev = IntArray(b.length + 1) { it }
            var cur = IntArray(b.length + 1)
            for (i in 1..a.length) {
                cur[0] = i
                var rowMin = cur[0]
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
                    if (cur[j] < rowMin) rowMin = cur[j]
                }
                if (rowMin >= limit) return limit
                val t = prev; prev = cur; cur = t
            }
            return minOf(prev[b.length], limit)
        }
    }
}
