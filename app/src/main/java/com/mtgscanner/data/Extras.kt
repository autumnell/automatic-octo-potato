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
    /** "NM", "LP", "MP", "HP" or "DMG". */
    val condition: String = "NM",
    /** Scryfall language code; "en" is English. */
    val lang: String = "en",
)

/** A card you have more copies of than you need, and which rows to move to free them up. */
data class ExtraGroup(
    val key: String,
    val name: String,
    /** Copies outside trade binders, decks and the extras and bulk boxes. */
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
     * Boxes whose copies aren't counted or moved: trade binders, decks, and the extras and bulk boxes,
     * as in [Sorting.isMainBox]. [boxes] are (box id, kind). Pass the result to [find].
     */
    fun skippedBoxes(boxes: List<Pair<Long, String>>, policy: SortPolicy): Set<Long> =
        boxes.filterNot { (id, kind) -> Sorting.isMainBox(BoxInfo(id, BoxRules(), kind), policy) }.map { it.first }.toSet()

    /**
     * Finds cards with more than [keep] copies outside [skipBoxIds] (see [skippedBoxes]). The copies suggested
     * for moving are the worst condition first, then non-English, then the newest, so the copies you keep
     * are your best ones and the first ones you filed.
     */
    fun find(rows: List<ExtraRow>, keep: Int, skipBoxIds: Set<Long>): List<ExtraGroup> =
        rows.filter { it.boxId !in skipBoxIds && !unlimited(it.typeLine, it.name) }
            .groupBy { it.key }
            .mapNotNull { (key, group) ->
                val owned = group.sumOf { it.quantity }
                if (owned <= keep) return@mapNotNull null
                var left = owned - keep
                val moves = mutableListOf<Pair<Long, Int>>()
                var value = 0.0
                for (r in group.sortedWith(MOVE_FIRST)) {
                    if (left == 0) break
                    val n = minOf(left, r.quantity)
                    moves += r.id to n
                    value += (r.price ?: 0.0) * n
                    left -= n
                }
                ExtraGroup(key, group.first().name, owned, keep, moves, value)
            }
            .sortedWith(compareByDescending<ExtraGroup> { it.extra }.thenBy { it.name })

    /** Condition from best to worst. */
    private val CONDITIONS = listOf("NM", "LP", "MP", "HP", "DMG")

    fun conditionRank(condition: String): Int = CONDITIONS.indexOf(condition.uppercase()).coerceAtLeast(0)

    private val MOVE_FIRST: Comparator<ExtraRow> = compareByDescending<ExtraRow> { conditionRank(it.condition) }
        .thenBy { it.lang.isBlank() || it.lang.equals("en", ignoreCase = true) }
        .thenByDescending { it.addedAt }

    private val ANY_NUMBER = setOf(
        "Relentless Rats", "Rat Colony", "Shadowborn Apostle", "Persistent Petitioners", "Dragon's Approach",
        "Slime Against Humanity", "Hare Apparent", "Templar Knight", "Cid, Timeless Artificer", "Seven Dwarves",
        "Nazgûl", "Tempest Hawk",
    )
}
