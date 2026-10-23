package com.mtgscanner.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import com.mtgscanner.data.db.Box
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.facts
import com.mtgscanner.data.db.isDeckSlot
import com.mtgscanner.data.db.placeName

/** Binder pages: 9 pockets each, slots numbered from 1. */
object Binder {
    const val POCKETS = 9

    fun page(slot: Int): Int = (slot - 1) / POCKETS + 1
    fun pocket(slot: Int): Int = (slot - 1) % POCKETS + 1

    /** "Page 4 · Slot 7", or null when the card has no slot. */
    fun label(slot: Int): String? = if (slot <= 0) null else "Page ${page(slot)} · Slot ${pocket(slot)}"

    /** Pockets a row takes: one per copy in a binder set to [perCopy], else one. */
    fun span(quantity: Int, perCopy: Boolean): Int = if (perCopy) quantity.coerceAtLeast(1) else 1

    /** Like [label] for a row taking [span] pockets: "Page 4 · Slots 7–9", "Page 4 · Slot 8 to Page 5 · Slot 1". */
    fun label(slot: Int, span: Int): String? {
        if (slot <= 0) return null
        if (span <= 1) return label(slot)
        val last = slot + span - 1
        return if (page(slot) == page(last)) "Page ${page(slot)} · Slots ${pocket(slot)}–${pocket(last)}"
        else "${label(slot)} to ${label(last)}"
    }

    fun isBinder(kind: String): Boolean = kind == "binder" || kind == "trade"

    /**
     * Slots for entries that have none (or share a pocket with an earlier entry): the next free
     * slots after the last one used, in name order. [span] is how many pockets an entry takes (one per
     * copy in a binder set that way). Returns entry id → new slot.
     */
    fun <T> numberSlots(
        items: List<T>, id: (T) -> Long, name: (T) -> String, slot: (T) -> Int, span: (T) -> Int = { 1 },
    ): Map<Long, Int> {
        val taken = HashSet<Int>()
        val unplaced = mutableListOf<T>()
        for (item in items.sortedBy { i -> slot(i) }) {
            val s = slot(item)
            val pockets = (s until s + span(item).coerceAtLeast(1)).toList()
            if (s > 0 && pockets.none { it in taken }) { taken += pockets; continue }
            unplaced += item
        }
        var next = (taken.maxOrNull() ?: 0) + 1
        val out = LinkedHashMap<Long, Int>()
        for (item in unplaced.sortedBy { Sorting.nameKey(name(it)) }) {
            out[id(item)] = next
            next += span(item).coerceAtLeast(1)
        }
        return out
    }

    /** Pocket → entry for the page view; an entry fills [span] pockets from its slot. */
    fun <T> pockets(items: List<T>, slot: (T) -> Int, span: (T) -> Int): Map<Int, T> {
        val out = HashMap<Int, T>()
        for (item in items) {
            val s = slot(item)
            if (s <= 0) continue
            for (p in s until s + span(item).coerceAtLeast(1)) out.putIfAbsent(p, item)
        }
        return out
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

object Placement {
    /** Facts stand-in for a box without dividers: placing by name alone needs none. */
    private val NO_FACTS = CardFacts("", "", "", emptySet(), false, null, "")

    /** A box's cards as (name, facts) for [Sorting.position]; facts are only worked out when the layout needs them. */
    fun contents(layout: BoxLayout, boxCards: List<CollectionCard>): List<Pair<String, CardFacts>> =
        boxCards.map { it.name to if (needsFacts(layout)) it.facts() else NO_FACTS }

    private fun needsFacts(layout: BoxLayout) = layout.hasDividers || layout.byNumber

    /**
     * Where a card in [box] sits: its binder slot, or its divider and neighbors by name.
     * [boxContents] is [contents] of the box, when already worked out.
     */
    fun whereIs(
        card: CollectionCard, box: Box?, boxCards: List<CollectionCard>,
        boxContents: List<Pair<String, CardFacts>>? = null,
    ): String {
        val boxName = box?.name ?: "No box"
        // A deck has no spots inside.
        if (box != null && box.isDeckSlot) return box.placeName()
        if (box != null && Binder.isBinder(box.kind) && card.binderSlot > 0) {
            return "$boxName → ${Binder.label(card.binderSlot, Binder.span(card.quantity, box.pocketPerCopy))}"
        }
        val layout = BoxLayout.of(box?.divider.orEmpty())
        val key = Sorting.nameKey(card.name)
        val others = (boxContents ?: contents(layout, boxCards)).filter { Sorting.nameKey(it.first) != key }
        val pos = Sorting.position(layout, card.name, if (needsFacts(layout)) card.facts() else NO_FACTS, others)
        return Sorting.where(boxName, pos)
    }

    /** Every entry whose name contains [query], best matches first, with its box and spot. */
    fun find(query: String, cards: List<CollectionCard>, boxes: List<Box>, limit: Int = 60): List<Found> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        val byId = boxes.associateBy { it.id }
        val byBox = cards.groupBy { it.boxId }
        // Each box's contents are worked out once per search, not once per card found.
        val boxContents = HashMap<Long, List<Pair<String, CardFacts>>>()
        return cards.filter { it.name.lowercase().contains(q) }
            .sortedWith(compareBy<CollectionCard>({ it.name.lowercase() != q }, { !it.name.lowercase().startsWith(q) }, { it.name }, { it.setCode }))
            .take(limit)
            .map { c ->
                val box = byId[c.boxId]
                val inBox = byBox[c.boxId].orEmpty()
                val list = boxContents.getOrPut(c.boxId) { contents(BoxLayout.of(box?.divider.orEmpty()), inBox) }
                Found(c, box?.placeName() ?: "No box", whereIs(c, box, inBox, list))
            }
    }
}

/**
 * Card search ("Where is it?"): every box (or deck) a card is in, with its spot. Reads the collection list the app
 * already keeps in memory rather than the whole table on every search.
 */
suspend fun CollectionRepository.findCard(query: String): List<Found> = Placement.find(query, observeAll().first(), dao.allPlaces())

/** Gives binder slots to the cards in [boxId] that have none. Returns how many were numbered. */
suspend fun CollectionRepository.numberBinderSlots(boxId: Long): Int = db.withTransaction {
    val cards = dao.inBox(boxId)
    val perCopy = dao.box(boxId)?.pocketPerCopy == true
    val slots = Binder.numberSlots(cards, { it.id }, { it.name }, { it.binderSlot }, { Binder.span(it.quantity, perCopy) })
    val now = System.currentTimeMillis()
    for (c in cards) slots[c.id]?.let { dao.update(c.copy(binderSlot = it, updatedAt = now)) }
    slots.size
}

/** Sets a binder to one pocket per copy (or one per card). Slots that now overlap are numbered again by [numberBinderSlots]. */
suspend fun CollectionRepository.setPocketPerCopy(boxId: Long, on: Boolean) {
    val b = dao.box(boxId) ?: return
    if (b.pocketPerCopy != on) dao.updateBox(b.copy(pocketPerCopy = on))
}

/** "Stay in this box" on or off for these rows. */
suspend fun CollectionRepository.setPinned(rowIds: List<Long>, pinned: Boolean) {
    rowIds.distinct().chunked(500).forEach { dao.setPinned(it, pinned) }
}

/**
 * Sets condition, language and finish on several rows at once (null leaves that one alone). Rows that end up
 * alike merge, as in [CollectionRepository.update]. Returns how many rows changed.
 */
suspend fun CollectionRepository.bulkEdit(rowIds: List<Long>, condition: String?, lang: String?, foil: Boolean?): Int {
    var n = 0
    for (id in rowIds.distinct()) {
        val c = cardOnce(id) ?: continue
        var row = c
        if (condition != null) row = row.copy(condition = condition)
        if (lang != null) row = row.copy(lang = lang)
        var at = id
        if (row != c) { at = update(row); n++ }
        if (foil != null && c.foil != foil) { setFoil(at, foil); if (row == c) n++ }
    }
    return n
}
