package com.mtgscanner.data

import com.mtgscanner.data.db.Box
import com.mtgscanner.data.db.CollectionCard

/** A box to make or change in a box plan. [id] null makes a new box. */
data class BoxChange(
    val id: Long?,
    val name: String,
    val kind: String,
    val rules: BoxRules,
    val color: Int = 0,
    val capacity: Int = 0,
    val divider: String = "",
    /** A new box that takes cards no other box fits, when there's no such box yet. */
    val fallback: Boolean = false,
)

/** Where every card would go with [changes] made, without saving anything. */
suspend fun CollectionRepository.planBoxes(changes: List<BoxChange>): BoxPlanResult<CollectionCard> {
    val names = dao.allBoxes().associate { it.id to it.name }
    val current = boxInfos().map { BoxPlans.Planned(it, names[it.id] ?: "Box") }
    val edits = changes.mapNotNull { c -> c.id?.let { id -> id to BoxInfo(id, c.rules, c.kind, c.capacity) } }.toMap()
    val added = changes.filter { it.id == null }.map { BoxInfo(0, it.rules, it.kind, it.capacity, fallback = it.fallback) to it.name }
    val planned = BoxPlans.boxes(current, edits, added).map { p ->
        // A renamed box shows its new name.
        if (p.isNew) p else changes.firstOrNull { it.id == p.info.id }?.let { p.copy(name = it.name) } ?: p
    }
    val moves = misplaced(dao.all(), planned.map { it.info })
    return BoxPlans.summarize(planned, moves) { it.quantity }
}

/** What saving a box plan changed, so Undo can put the boxes back. */
class BoxPlanUndo internal constructor(
    internal val oldBoxes: List<Box>,
    internal val oldFallback: Long?,
    internal val made: List<Long>,
)

/** Saves the boxes in a plan: changed boxes updated, new ones made at the end of the Auto-sort order. */
suspend fun CollectionRepository.saveBoxChanges(changes: List<BoxChange>): BoxPlanUndo {
    val all = dao.allBoxes()
    val old = changes.mapNotNull { c -> c.id?.let { id -> all.firstOrNull { it.id == id } } }
    val oldFallback = all.firstOrNull { it.fallback }?.id
    var hasFallback = oldFallback != null
    val made = ArrayList<Long>()
    for (c in changes) {
        val id = c.id
        if (id != null) {
            updateBox(id, c.name, c.kind, c.rules, c.color, capacity = c.capacity, divider = c.divider)
        } else {
            val newId = createBox(c.name, c.kind, c.rules, c.color)
            if (c.capacity > 0 || c.divider.isNotEmpty()) setBoxLayout(newId, c.capacity, c.divider)
            if (c.fallback && !hasFallback) {
                setFallbackBox(newId)
                hasFallback = true
            }
            made += newId
        }
    }
    return BoxPlanUndo(old, oldFallback, made)
}

/** Puts boxes back as they were before [saveBoxChanges]. Run after the cards are restored, so new boxes are empty. */
suspend fun CollectionRepository.undoBoxChanges(u: BoxPlanUndo) {
    for (b in u.oldBoxes) if (dao.box(b.id) != null) dao.updateBox(b)
    for (id in u.made) if (dao.box(id) != null) deleteBox(id, Box.AUTO_SORT)
    setFallbackBox(u.oldFallback)
}
