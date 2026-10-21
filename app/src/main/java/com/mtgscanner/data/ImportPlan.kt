package com.mtgscanner.data

import com.mtgscanner.data.db.Box
import com.mtgscanner.util.ImportRow

/** What an import will do: each row with the card found for it (null = not found) and the box it goes to. */
data class ImportPlan(
    val rows: List<ImportRow>,
    val found: List<ScryfallCard?>,
    /** Box id per row; [NEW_BOX] = a new "Imported" box; null for rows not found. */
    val targets: List<Long?>,
    /** The box choice it was planned for: a box id, [Box.AUTO_SORT], or 0 for a new box. */
    val choice: Long,
) {
    val added: Int get() = rows.indices.filter { found[it] != null }.sumOf { rows[it].quantity }
    val notFound: List<Int> get() = rows.indices.filter { found[it] == null }

    /** Copies per box, in the order boxes first appear. */
    fun perBox(): List<Pair<Long, Int>> = ImportPlanner.perBox(targets, rows.map { it.quantity })

    companion object {
        const val NEW_BOX = 0L
    }
}

/** Pure sorting logic for imports, so the preview and the real import pick the same boxes. */
object ImportPlanner {
    /** One found card for planning: its facts, oracle id and copies. */
    data class Item(val facts: CardFacts, val oracleId: String?, val quantity: Int)

    /**
     * Picks a box per item (null item = not found → null). With [choice] = [Box.AUTO_SORT] it runs
     * [Sorting.decide] row by row, counting cards added earlier in the same import (box fill and the
     * playset limit), just like adding them one at a time. Cards no box fits, and every card when
     * [choice] is 0, get [ImportPlan.NEW_BOX]. [ownedInMain] gives copies already in main boxes per oracle id.
     */
    fun targets(
        items: List<Item?>,
        choice: Long,
        boxes: List<BoxInfo>,
        policy: SortPolicy,
        ownedInMain: (String) -> Int,
    ): List<Long?> {
        val live = boxes.toMutableList()
        val added = HashMap<String, Int>()
        val base = HashMap<String, Int>()
        return items.map { item ->
            if (item == null) return@map null
            when (choice) {
                ImportPlan.NEW_BOX -> ImportPlan.NEW_BOX
                Box.AUTO_SORT -> {
                    val oid = item.oracleId
                    val owned = if (oid == null) 0 else base.getOrPut(oid) { ownedInMain(oid) } + (added[oid] ?: 0)
                    val d = Sorting.decide(live, item.facts, policy, owned)
                    val target = d?.boxId ?: ImportPlan.NEW_BOX
                    val i = live.indexOfFirst { it.id == target }
                    if (i >= 0) live[i] = live[i].copy(count = live[i].count + item.quantity)
                    // The new box is a plain box, so its copies count as kept in main boxes.
                    val main = if (i >= 0) Sorting.isMainBox(live[i], policy) else true
                    if (oid != null && main) added[oid] = (added[oid] ?: 0) + item.quantity
                    target
                }
                else -> choice
            }
        }
    }

    /** Copies per target, in first-seen order; rows with no target are skipped. */
    fun perBox(targets: List<Long?>, quantities: List<Int>): List<Pair<Long, Int>> {
        val out = LinkedHashMap<Long, Int>()
        targets.forEachIndexed { i, t -> if (t != null) out[t] = (out[t] ?: 0) + quantities[i] }
        return out.toList()
    }
}
