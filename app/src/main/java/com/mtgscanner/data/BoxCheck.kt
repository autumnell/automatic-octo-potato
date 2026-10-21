package com.mtgscanner.data

/** A row of the box being checked, as recorded in the app. */
data class BoxRow(val rowId: Long, val scryfallId: String, val oracleId: String?, val name: String, val foil: Boolean, val quantity: Int)

/** Copies of one printing scanned from the real box. */
data class BoxScan(val scryfallId: String, val oracleId: String?, val name: String, val foil: Boolean, val count: Int)

/**
 * A box check: [found] rows with the copies seen (scanned or ticked), [missing] rows with copies the app has but
 * weren't seen, [notRecorded] scans with copies the app doesn't have in this box.
 */
data class BoxCheckResult(
    val found: List<Pair<BoxRow, Int>>,
    val missing: List<Pair<BoxRow, Int>>,
    val notRecorded: List<Pair<BoxScan, Int>>,
) {
    val foundCount: Int get() = found.sumOf { it.second }
    val missingCount: Int get() = missing.sumOf { it.second }
    val notRecordedCount: Int get() = notRecorded.sumOf { it.second }
}

/** Compares a box's rows with the cards scanned from it. Pure Kotlin for tests. */
object BoxCheck {
    /**
     * Scans count against the same printing and finish first, then the same printing, then any printing of the
     * card (a name-only read can give another printing). [ticked] is copies marked found by hand, by row id.
     */
    fun compare(rows: List<BoxRow>, scans: List<BoxScan>, ticked: Map<Long, Int> = emptyMap()): BoxCheckResult {
        val seen = IntArray(rows.size) { i -> (ticked[rows[i].rowId] ?: 0).coerceIn(0, rows[i].quantity) }
        val left = IntArray(scans.size) { scans[it].count }
        fun pass(match: (BoxRow, BoxScan) -> Boolean) {
            for (s in scans.indices) {
                if (left[s] <= 0) continue
                for (r in rows.indices) {
                    if (left[s] <= 0) break
                    val room = rows[r].quantity - seen[r]
                    if (room <= 0 || !match(rows[r], scans[s])) continue
                    val take = minOf(room, left[s])
                    seen[r] += take
                    left[s] -= take
                }
            }
        }
        pass { r, s -> r.scryfallId == s.scryfallId && r.foil == s.foil }
        pass { r, s -> r.scryfallId == s.scryfallId }
        pass { r, s -> if (r.oracleId != null && s.oracleId != null) r.oracleId == s.oracleId else r.name.equals(s.name, ignoreCase = true) }
        val found = rows.indices.filter { seen[it] > 0 }.map { rows[it] to seen[it] }
        val missing = rows.indices.filter { rows[it].quantity > seen[it] }.map { rows[it] to rows[it].quantity - seen[it] }
        val notRecorded = scans.indices.filter { left[it] > 0 }.map { scans[it] to left[it] }
        return BoxCheckResult(found, missing, notRecorded)
    }
}
