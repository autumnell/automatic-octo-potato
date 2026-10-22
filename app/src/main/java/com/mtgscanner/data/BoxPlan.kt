package com.mtgscanner.data

/** One box in a box plan: cards in it now and after the plan, against what it holds. */
data class PlanRow(
    val id: Long,
    val name: String,
    /** Made by the plan. */
    val isNew: Boolean,
    /** Its rules change in the plan. */
    val changed: Boolean,
    val before: Int,
    val after: Int,
    /** Most cards it holds; 0 = no limit set. */
    val capacity: Int,
) {
    val change: Int get() = after - before

    /** Real boxes needed for [after] cards: by its own capacity, else [boxSize] (0 = don't say). */
    fun boxesNeeded(boxSize: Int): Int? {
        val cap = if (capacity > 0) capacity else boxSize
        if (cap <= 0 || after <= 0) return null
        return ((after + cap - 1) / cap).coerceAtLeast(1)
    }

    /** Cards over its capacity after the plan; 0 when it fits or has no limit. */
    val over: Int get() = if (capacity > 0) (after - capacity).coerceAtLeast(0) else 0
}

data class BoxPlanResult<T>(val rows: List<PlanRow>, val moves: List<Sorting.Move<T>>, val cardsMoving: Int)

/**
 * "Try a box plan first": where the whole collection would go under changed or new boxes, worked out with the
 * same re-sort rules ([Sorting.misplaced]) that Apply then uses. Pure, for tests.
 */
object BoxPlans {
    /** A box as it would be: its sorting info and name. */
    data class Planned(val info: BoxInfo, val name: String, val isNew: Boolean = false, val changed: Boolean = false)

    /** Stand-in ids for boxes the plan would make; far from real ids and from [com.mtgscanner.data.db.Box.AUTO_SORT]. */
    fun newId(i: Int): Long = Long.MIN_VALUE / 2 + i

    /**
     * The boxes as they'd be: [current] in Auto-sort order with [edits] (by id) swapped in, then [added] at the end
     * (new boxes go last, as [CollectionRepository.createBox] puts them). Only the first [Planned] marked fallback
     * keeps it, and a new box can't take it from an existing one.
     */
    fun boxes(current: List<Planned>, edits: Map<Long, BoxInfo>, added: List<Pair<BoxInfo, String>>): List<Planned> {
        val kept = current.map { p ->
            val e = edits[p.info.id] ?: return@map p
            p.copy(info = e.copy(id = p.info.id, count = p.info.count, fallback = p.info.fallback), changed = true)
        }
        var hasFallback = kept.any { it.info.fallback }
        val made = added.mapIndexed { i, (info, name) ->
            val fb = info.fallback && !hasFallback
            if (fb) hasFallback = true
            Planned(info.copy(id = newId(i), count = 0, fallback = fb), name, isNew = true)
        }
        return kept + made
    }

    /** Counts per box once [moves] are made. Rows come in Auto-sort order. */
    fun <T> summarize(planned: List<Planned>, moves: List<Sorting.Move<T>>, copies: (T) -> Int): BoxPlanResult<T> {
        val delta = HashMap<Long, Int>()
        var n = 0
        for (m in moves) {
            val c = copies(m.item)
            delta[m.from] = (delta[m.from] ?: 0) - c
            delta[m.to] = (delta[m.to] ?: 0) + c
            n += c
        }
        val rows = planned.map { p ->
            PlanRow(p.info.id, p.name, p.isNew, p.changed, p.info.count, p.info.count + (delta[p.info.id] ?: 0), p.info.capacity)
        }
        return BoxPlanResult(rows, moves, n)
    }

    /** "412 cards · needs 2 boxes", "Empty". A box with its own capacity is measured by that, others by [boxSize]. */
    fun label(row: PlanRow, boxSize: Int): String {
        if (row.after == 0) return "Empty"
        val cards = if (row.after == 1) "1 card" else "${row.after} cards"
        val need = row.boxesNeeded(boxSize)
        return if (need != null && need > 1) "$cards · needs $need boxes" else cards
    }
}
