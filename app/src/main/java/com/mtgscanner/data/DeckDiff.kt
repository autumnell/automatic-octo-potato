package com.mtgscanner.data

import com.mtgscanner.util.CardNames
import com.mtgscanner.util.DeckLine
import com.mtgscanner.util.DecklistParser

/** One card in a list change: [qty] copies of [name]; [printing] is "CMR 263", or null when the list names none. */
data class DiffLine(val key: String, val name: String, val qty: Int, val printing: String? = null)

/** [qty] copies of [name] whose printing changed, [from] → [to]. */
data class DiffChange(val key: String, val name: String, val qty: Int, val from: String?, val to: String?)

/** What changes between a deck's saved list and a new one. Cards match by name (two-faced safe), then printing. */
data class DeckDiff(
    val added: List<DiffLine>,
    val removed: List<DiffLine>,
    val changed: List<DiffChange>,
    val unchanged: List<DiffLine>,
) {
    val isEmpty: Boolean get() = added.isEmpty() && removed.isEmpty() && changed.isEmpty()
    val addedCount: Int get() = added.sumOf { it.qty }
    val removedCount: Int get() = removed.sumOf { it.qty }
    val changedCount: Int get() = changed.sumOf { it.qty }
    val unchangedCount: Int get() = unchanged.sumOf { it.qty }
}

/** A row of the collection kept in the deck: its id, card name, printing and copies. */
data class SlotRow(val id: Long, val name: String, val setCode: String?, val number: String?, val qty: Int)

/** Pure: compares deck lists and picks the deck's own copies that leave with removed cards. */
object DeckDiffs {
    /** Destination for copies leaving a deck: the box Auto-sort picks. Same value as Box.AUTO_SORT. */
    const val AUTO = -2L
    /** Destination for copies leaving a deck: out of the collection. */
    const val REMOVE = -3L

    /** Lines that are real cards in the deck; the maybeboard and tokens aren't. */
    private fun cards(text: String): List<DeckLine> = DecklistParser.parse(text).filter { it.section in DeckAdd.SECTIONS }

    fun of(oldText: String, newText: String): DeckDiff = of(cards(oldText), cards(newText))

    fun of(old: List<DeckLine>, new: List<DeckLine>): DeckDiff {
        val oldBy = old.groupBy { CardNames.key(it.name) }
        val newBy = new.groupBy { CardNames.key(it.name) }
        val keys = (new.map { CardNames.key(it.name) } + old.map { CardNames.key(it.name) }).distinct()
        val added = mutableListOf<DiffLine>()
        val removed = mutableListOf<DiffLine>()
        val changed = mutableListOf<DiffChange>()
        val unchanged = mutableListOf<DiffLine>()
        for (k in keys) {
            val o = oldBy[k].orEmpty()
            val n = newBy[k].orEmpty()
            val name = (n.firstOrNull() ?: o.first()).name
            val oP = byPrinting(o)
            val nP = byPrinting(n)
            val oT = oP.values.sum()
            val nT = nP.values.sum()
            // Copies listed with the same printing in both.
            val same = oP.entries.sumOf { (p, q) -> minOf(q, nP[p] ?: 0) }
            // Printings that gained or lost copies: what an added, removed or changed copy is shown as.
            val gained = nP.entries.firstOrNull { (p, q) -> q > (oP[p] ?: 0) }?.key
            val lost = oP.entries.firstOrNull { (p, q) -> q > (nP[p] ?: 0) }?.key
            if (nT > oT) added += DiffLine(k, name, nT - oT, gained?.label)
            if (oT > nT) removed += DiffLine(k, name, oT - nT, lost?.label)
            val moved = minOf(oT, nT) - same
            if (moved > 0) changed += DiffChange(k, name, moved, lost?.label, gained?.label)
            if (same > 0) unchanged += DiffLine(k, name, same, if (oP.size == 1) oP.keys.first().label else null)
        }
        return DeckDiff(added, removed, changed, unchanged)
    }

    private data class Printing(val set: String?, val number: String?, val foil: Boolean) {
        val label: String? get() = set?.let { s -> s.uppercase() + (number?.let { " $it" } ?: "") }
    }

    private fun byPrinting(lines: List<DeckLine>): Map<Printing, Int> {
        val out = linkedMapOf<Printing, Int>()
        for (l in lines) {
            val p = Printing(l.setCode?.lowercase(), l.collectorNumber?.lowercase(), l.foil)
            out[p] = (out[p] ?: 0) + l.quantity
        }
        return out
    }

    /** Copies of a card the new list needs in all, by name key. */
    fun totals(text: String): Map<String, Int> =
        cards(text).groupBy { CardNames.key(it.name) }.mapValues { (_, v) -> v.sumOf { it.quantity } }

    /**
     * The deck's own copies that leave with a removed card: as many as were [removed], but never so many that
     * fewer than [stillNeeded] stay. Rows of a printing the new list doesn't name go first.
     * Returns (row id, copies) pairs.
     */
    fun leaving(
        slot: List<SlotRow>,
        key: String,
        removed: Int,
        stillNeeded: Int,
        keepPrintings: Set<String> = emptySet(),
    ): List<Pair<Long, Int>> {
        val rows = slot.filter { CardNames.key(it.name) == key && it.qty > 0 }
        val inDeck = rows.sumOf { it.qty }
        var left = minOf(removed, inDeck - stillNeeded)
        if (left <= 0) return emptyList()
        fun kept(r: SlotRow) = printingKey(r.setCode, r.number) in keepPrintings || printingKey(r.setCode, null) in keepPrintings
        val ordered = rows.sortedBy { if (kept(it)) 1 else 0 }
        val out = mutableListOf<Pair<Long, Int>>()
        for (r in ordered) {
            if (left <= 0) break
            val n = minOf(left, r.qty)
            out += r.id to n
            left -= n
        }
        return out
    }

    /** "cmr|263": how [leaving] tells printings apart. A null number matches any number in the set. */
    fun printingKey(set: String?, number: String?): String = "${set.orEmpty().lowercase()}|${number.orEmpty().lowercase()}"

    /** Printing keys the new list names for [key], set alone and set with number. */
    fun keptPrintings(text: String, key: String): Set<String> =
        cards(text).filter { CardNames.key(it.name) == key && it.setCode != null }.flatMap {
            listOf(printingKey(it.setCode, it.collectorNumber), printingKey(it.setCode, null))
        }.toSet()
}
