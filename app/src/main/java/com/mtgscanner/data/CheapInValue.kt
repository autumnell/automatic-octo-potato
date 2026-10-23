package com.mtgscanner.data

/**
 * Cards now priced under the bulk floor that still sit in a value box, and where Auto-sort would put them now
 * ([Sorting.decide]). Pure, for tests.
 */
object CheapInValue {
    /** A box kept for value: a minimum price rule, or rares and mythics only. */
    fun isValueBox(rules: BoxRules): Boolean =
        (rules.minPrice ?: 0.0) > 0.0 ||
            (rules.rarities.isNotEmpty() && rules.rarities.all { it.equals("rare", true) || it.equals("mythic", true) })

    data class Hit<T>(val item: T, val from: Long, val to: Long)

    /**
     * [boxes] in Auto-sort order. Needs a bulk floor in [policy]; cards with no price, [pinned] rows, trade
     * binders, decks and the bulk box itself are left alone, as are cards Auto-sort would keep where they are.
     */
    fun <T> find(
        items: List<T>,
        boxOf: (T) -> Long,
        facts: (T) -> CardFacts,
        boxes: List<BoxInfo>,
        policy: SortPolicy,
        pinned: (T) -> Boolean = { false },
    ): List<Hit<T>> {
        val floor = policy.bulkFloor ?: return emptyList()
        val byId = boxes.associateBy { it.id }
        val noPlayset = policy.copy(playsetLimit = 0)
        return items.mapNotNull { item ->
            if (pinned(item)) return@mapNotNull null
            val from = byId[boxOf(item)] ?: return@mapNotNull null
            if (from.kind == "trade" || from.kind == "deck" || from.id == policy.bulkBoxId || !isValueBox(from.rules)) return@mapNotNull null
            val f = facts(item)
            val price = f.price ?: return@mapNotNull null
            if (price >= floor) return@mapNotNull null
            val d = Sorting.decide(boxes, f, noPlayset) ?: return@mapNotNull null
            if (d.boxId == from.id) null else Hit(item, from.id, d.boxId)
        }
    }
}
