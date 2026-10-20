package com.mtgscanner.data

/** A collection row, as far as finding extra copies is concerned. */
data class ExtraRow(
    val id: Long,
    /** Same card, any printing: the oracle id, or the name. */
    val key: String,
    val name: String,
    val quantity: Int,
    val boxId: Long,
    val addedAt: Long,
    val typeLine: String,
    val price: Double?,
)

/** A card you have more copies of than you need, and which rows to move to free them up. */
data class ExtraGroup(
    val key: String,
    val name: String,
    /** Copies outside trade binders. */
    val owned: Int,
    val keep: Int,
    /** Row id to number of copies to move. */
    val moves: List<Pair<Long, Int>>,
    val extraValue: Double,
) {
    val extra: Int get() = moves.sumOf { it.second }
}

object ExtrasFinder {
    /** Basic lands and cards that say a deck can have any number of them aren't counted. */
    fun unlimited(typeLine: String, name: String): Boolean =
        (typeLine.startsWith("Basic") && typeLine.contains("Land")) || name in ANY_NUMBER

    /**
     * Finds cards with more than [keep] copies outside [tradeBoxIds]. The newest copies are the
     * ones suggested for moving, so the first ones you filed stay where they are.
     */
    fun find(rows: List<ExtraRow>, keep: Int, tradeBoxIds: Set<Long>): List<ExtraGroup> =
        rows.filter { it.boxId !in tradeBoxIds && !unlimited(it.typeLine, it.name) }
            .groupBy { it.key }
            .mapNotNull { (key, group) ->
                val owned = group.sumOf { it.quantity }
                if (owned <= keep) return@mapNotNull null
                var left = owned - keep
                val moves = mutableListOf<Pair<Long, Int>>()
                var value = 0.0
                for (r in group.sortedByDescending { it.addedAt }) {
                    if (left == 0) break
                    val n = minOf(left, r.quantity)
                    moves += r.id to n
                    value += (r.price ?: 0.0) * n
                    left -= n
                }
                ExtraGroup(key, group.first().name, owned, keep, moves, value)
            }
            .sortedWith(compareByDescending<ExtraGroup> { it.extra }.thenBy { it.name })

    private val ANY_NUMBER = setOf(
        "Relentless Rats", "Rat Colony", "Shadowborn Apostle", "Persistent Petitioners", "Dragon's Approach",
        "Slime Against Humanity", "Hare Apparent", "Templar Knight", "Cid, Timeless Artificer", "Seven Dwarves",
        "Nazgûl", "Tempest Hawk",
    )
}
