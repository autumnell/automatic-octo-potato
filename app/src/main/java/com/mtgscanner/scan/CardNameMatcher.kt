package com.mtgscanner.scan

/**
 * Matches OCR text against every Magic card name, on the phone, with no network.
 * Double-faced cards can be matched by either face.
 */
class CardNameMatcher(names: Collection<String>) {
    data class Match(val name: String, val score: Double)

    /** normalized face name -> full card name */
    private val byNorm = HashMap<String, String>(names.size * 2)
    /** normalized names grouped by length, so only similar lengths are compared */
    private val byLength = HashMap<Int, MutableList<String>>()

    init {
        for (full in names) {
            val faces = listOf(full) + if (" // " in full) full.split(" // ") else emptyList()
            for (face in faces) {
                val n = CardTextParser.normalize(face)
                if (n.isEmpty() || byNorm.containsKey(n)) continue
                byNorm[n] = full
                byLength.getOrPut(n.length) { mutableListOf() } += n
            }
        }
    }

    val size: Int get() = byNorm.size

    /** The closest card name to [text], or null when nothing is close (score below [minScore]). */
    fun best(text: String, minScore: Double = 0.72): Match? {
        val q = CardTextParser.normalize(text)
        if (q.length < 3) return null
        byNorm[q]?.let { return Match(it, 1.0) }
        val slack = maxOf(2, q.length / 4)
        var bestName: String? = null
        var bestDist = Int.MAX_VALUE
        var bestLen = 1
        for (len in (q.length - slack)..(q.length + slack)) {
            val bucket = byLength[len] ?: continue
            for (n in bucket) {
                val d = boundedLevenshtein(q, n, minOf(bestDist, slack + 1))
                if (d < bestDist) {
                    bestDist = d; bestName = n; bestLen = maxOf(q.length, n.length)
                    if (d == 0) break
                }
            }
        }
        val name = bestName ?: return null
        val score = 1.0 - bestDist.toDouble() / bestLen
        return if (score >= minScore) Match(byNorm.getValue(name), score) else null
    }

    companion object {
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
