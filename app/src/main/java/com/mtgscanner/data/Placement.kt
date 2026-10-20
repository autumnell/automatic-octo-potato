package com.mtgscanner.data

import androidx.room.withTransaction
import com.mtgscanner.data.db.Box
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.facts

/** Binder pages: 9 pockets each, slots numbered from 1. */
object Binder {
    const val POCKETS = 9

    fun page(slot: Int): Int = (slot - 1) / POCKETS + 1
    fun pocket(slot: Int): Int = (slot - 1) % POCKETS + 1

    /** "Page 4 · Slot 7", or null when the card has no slot. */
    fun label(slot: Int): String? = if (slot <= 0) null else "Page ${page(slot)} · Slot ${pocket(slot)}"

    fun isBinder(kind: String): Boolean = kind == "binder" || kind == "trade"

    /**
     * Slots for entries that have none (or share a slot with an earlier entry): the next free
     * slots after the last one used, in name order. Returns entry id → new slot.
     */
    fun <T> numberSlots(items: List<T>, id: (T) -> Long, name: (T) -> String, slot: (T) -> Int): Map<Long, Int> {
        val taken = HashSet<Int>()
        val unplaced = mutableListOf<T>()
        for (item in items.sortedBy { i -> slot(i) }) {
            val s = slot(item)
            if (s > 0 && taken.add(s)) continue
            unplaced += item
        }
        var next = (taken.maxOrNull() ?: 0) + 1
        return unplaced.sortedBy { Sorting.nameKey(name(it)) }.associate { id(it) to next++ }
    }
}

/** One line of a physical move list: take [qty] of [name] from [from] to [to]. */
data class MoveLine(
    val qty: Int,
    val name: String,
    val setCode: String,
    val from: String,
    val to: String,
    /** Divider or binder slot in the new box. */
    val spot: String? = null,
)

object MoveLists {
    /** Plain text for printing or sharing, grouped by where cards go. */
    fun text(lines: List<MoveLine>, done: Set<Int> = emptySet()): String = buildString {
        append("Move list (").append(lines.sumOf { it.qty }).append(" cards)\n")
        lines.withIndex().groupBy { it.value.from to it.value.to }.forEach { (route, items) ->
            append("\n").append(route.first).append(" → ").append(route.second).append("\n")
            items.sortedBy { Sorting.nameKey(it.value.name) }.forEach { (i, l) ->
                append(if (i in done) "☑ " else "☐ ")
                if (l.qty > 1) append(l.qty).append("× ")
                append(l.name)
                if (l.setCode.isNotBlank()) append(" (").append(l.setCode.uppercase()).append(")")
                l.spot?.let { append(" · ").append(it) }
                append("\n")
            }
        }
    }
}

/** A card found by the lost card finder, and where it physically is. */
data class Found(val card: CollectionCard, val boxName: String, val where: String)

/** Cards owned in more than one printing. */
data class DupGroup(val name: String, val cards: List<CollectionCard>) {
    val printings: Int get() = cards.map { it.scryfallId }.distinct().size
    val copies: Int get() = cards.sumOf { it.quantity }
}

object Placement {
    /** Where a card in [box] sits: its binder slot, or its divider and neighbors by name. */
    fun whereIs(card: CollectionCard, box: Box?, boxCards: List<CollectionCard>): String {
        val boxName = box?.name ?: "No box"
        if (box != null && Binder.isBinder(box.kind) && card.binderSlot > 0) return "$boxName → ${Binder.label(card.binderSlot)}"
        val key = Sorting.nameKey(card.name)
        val others = boxCards.filter { Sorting.nameKey(it.name) != key }.map { it.name to it.facts() }
        val pos = Sorting.position(Divider.of(box?.divider.orEmpty()), card.name, card.facts(), others)
        return Sorting.where(boxName, pos)
    }

    /** Every entry whose name contains [query], best matches first, with its box and spot. */
    fun find(query: String, cards: List<CollectionCard>, boxes: List<Box>, limit: Int = 60): List<Found> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        val byId = boxes.associateBy { it.id }
        val byBox = cards.groupBy { it.boxId }
        return cards.filter { it.name.lowercase().contains(q) }
            .sortedWith(compareBy<CollectionCard>({ it.name.lowercase() != q }, { !it.name.lowercase().startsWith(q) }, { it.name }, { it.setCode }))
            .take(limit)
            .map { c ->
                val box = byId[c.boxId]
                Found(c, box?.name ?: "No box", whereIs(c, box, byBox[c.boxId].orEmpty()))
            }
    }

    /** Cards you own in more than one printing, most printings first. */
    fun duplicates(cards: List<CollectionCard>): List<DupGroup> =
        cards.groupBy { it.oracleId ?: it.name.lowercase() }
            .values
            .map { g -> DupGroup(g.first().name, g.sortedWith(compareBy<CollectionCard>({ it.setCode }, { it.collectorNumber }))) }
            .filter { it.printings > 1 }
            .sortedWith(compareByDescending<DupGroup> { it.printings }.thenBy { it.name })
}

/**
 * Where a card goes inside [boxId], for "Rares → Blue → between Counterspell and Cryptic Command".
 * Null when the box doesn't exist.
 */
suspend fun CollectionRepository.positionFor(boxId: Long, name: String, facts: CardFacts): String? {
    val b = dao.box(boxId) ?: return null
    val contents = dao.inBox(boxId).map { it.name to it.facts() }
    return Sorting.where(b.name, Sorting.position(Divider.of(b.divider), name, facts, contents))
}

/** Lost card finder: every box a card is in, with its spot. */
suspend fun CollectionRepository.findCard(query: String): List<Found> = Placement.find(query, dao.all(), dao.allBoxes())

/** Gives binder slots to the cards in [boxId] that have none. Returns how many were numbered. */
suspend fun CollectionRepository.numberBinderSlots(boxId: Long): Int = db.withTransaction {
    val cards = dao.inBox(boxId)
    val slots = Binder.numberSlots(cards, { it.id }, { it.name }, { it.binderSlot })
    val now = System.currentTimeMillis()
    for (c in cards) slots[c.id]?.let { dao.update(c.copy(binderSlot = it, updatedAt = now)) }
    slots.size
}
