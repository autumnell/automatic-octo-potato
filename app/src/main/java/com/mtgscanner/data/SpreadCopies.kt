package com.mtgscanner.data

/**
 * Copies of one card kept in two or more boxes, and the box to gather them in: the one holding the most copies
 * (the earlier box in Auto-sort order on a tie). Pure, for tests.
 */
object SpreadCopies {
    /** One collection row: [number] is the collector number. */
    data class Row(val id: Long, val name: String, val setCode: String, val number: String, val boxId: Long, val qty: Int)

    /** A card spread over boxes: copies per box (most first), the box they go to, and each row to move there. */
    data class Group(
        val key: String,
        val name: String,
        /** Set code, when grouping by printing. */
        val setCode: String?,
        val perBox: List<Pair<Long, Int>>,
        val target: Long,
        /** Row id → [target]. */
        val moves: List<Pair<Long, Long>>,
        /** Copies in [moves]. */
        val toMove: Int,
    )

    /**
     * Cards whose copies sit in more than one of [boxOrder] (box ids in Auto-sort order). Rows in other boxes are
     * left out, so the caller skips trade binders, decks and the like by leaving them out of [boxOrder].
     * [byPrinting]: only copies of the same set and collector number count as the same card. A row stays where it
     * is when it doesn't [fit] the target box's rules (so it wouldn't just show up in "to sort" again).
     */
    fun find(
        rows: List<Row>,
        boxOrder: List<Long>,
        byPrinting: Boolean,
        fits: (Row, Long) -> Boolean = { _, _ -> true },
    ): List<Group> {
        val rank = HashMap<Long, Int>()
        boxOrder.forEachIndexed { i, id -> rank.putIfAbsent(id, i) }
        return rows.filter { it.boxId in rank && it.qty > 0 }
            .groupBy { key(it, byPrinting) }
            .mapNotNull { (k, rs) ->
                val perBox = rs.groupBy { it.boxId }
                    .map { (box, l) -> box to l.sumOf { it.qty } }
                    .sortedWith(compareByDescending<Pair<Long, Int>> { it.second }.thenBy { rank[it.first] ?: Int.MAX_VALUE })
                if (perBox.size < 2) return@mapNotNull null
                val target = perBox.first().first
                val moving = rs.filter { it.boxId != target && fits(it, target) }
                if (moving.isEmpty()) return@mapNotNull null
                Group(
                    key = k,
                    name = rs.first().name,
                    setCode = if (byPrinting) rs.first().setCode else null,
                    perBox = perBox,
                    target = target,
                    moves = moving.map { it.id to target },
                    toMove = moving.sumOf { it.qty },
                )
            }
            .sortedWith(compareBy<Group>({ NameKey.of(it.name) }, { it.setCode.orEmpty() }))
    }

    fun key(r: Row, byPrinting: Boolean): String {
        val n = r.name.trim().lowercase()
        return if (byPrinting) "$n|${r.setCode.lowercase()}|${r.number.lowercase()}" else n
    }
}
