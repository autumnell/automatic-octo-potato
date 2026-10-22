package com.mtgscanner.ui.decks

import com.mtgscanner.data.DeckCheck
import com.mtgscanner.data.db.Box
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.util.DeckLine

/** Copies of one printing to take out of one box. */
data class PullItem(
    /** Stays the same while the copies stay put; used for the checkmarks. */
    val key: String,
    val name: String,
    val qty: Int,
    val setCode: String,
    val collectorNumber: String,
    val foil: Boolean,
    val proxy: Boolean,
    val imageUrl: String?,
    /** The collection rows to take the copies from: (row id, copies). */
    val rows: List<Pair<Long, Int>> = emptyList(),
)

/** What to pull from one box. [boxId] is null for cards in no known box. */
data class PullGroup(val boxId: Long?, val boxName: String, val items: List<PullItem>)

data class PullMissing(val name: String, val qty: Int)

data class PullList(
    val groups: List<PullGroup>,
    val missing: List<PullMissing>,
    /** Copies already in this deck's own deck box, so not pulled. */
    val inDeckBox: Int,
) {
    val toPull: Int get() = groups.sumOf { g -> g.items.sumOf { it.qty } }

    /** Row moves (row id, copies) for the checked items, skipping any already in [deckBoxId]. */
    fun movesFor(checked: Set<String>, deckBoxId: Long? = null): List<Pair<Long, Int>> =
        groups.filter { deckBoxId == null || it.boxId != deckBoxId }
            .flatMap { g -> g.items.filter { it.key in checked }.flatMap { it.rows } }
}

object PullListMath {
    fun itemKey(c: CollectionCard): String = "${c.boxId}|${c.scryfallId}|${c.foil}|${c.proxy}"

    /** Deck boxes named like the deck hold its cards already. */
    fun isDeckBoxFor(box: Box, deckName: String): Boolean =
        box.kind == "deck" && deckName.isNotBlank() && box.name.trim().equals(deckName.trim(), ignoreCase = true)

    private fun matchesPrinting(c: CollectionCard, l: DeckLine): Boolean {
        val set = l.setCode ?: return false
        if (!c.setCode.equals(set, ignoreCase = true)) return false
        val num = l.collectorNumber ?: return true
        return c.collectorNumber.equals(num, ignoreCase = true)
    }

    private fun price(c: CollectionCard): Double =
        (if (c.foil) c.priceUsdFoil ?: c.priceUsd else c.priceUsd) ?: Double.MAX_VALUE

    /**
     * Which owned copies to pull for the played sections (main deck, commander, companion), by box.
     * Copies already in this deck's deck box count first and aren't pulled. Lines naming a printing take that
     * printing when owned; otherwise real cards come before proxies, cards in other decks' boxes last,
     * non-foil before foil, cheapest first. What's left over is missing.
     * [boxes] are in the Collection's order.
     */
    fun build(lines: List<DeckLine>, cards: List<CollectionCard>, boxes: List<Box>, deckName: String): PullList {
        val boxById = boxes.associateBy { it.id }
        val ownBox = boxes.filter { isDeckBoxFor(it, deckName) }.map { it.id }.toSet()
        val left = cards.associate { it.id to it.quantity }.toMutableMap()
        val byName = cards.groupBy { DeckNames.key(it.name) }
        val played = lines.filter { it.section in DeckCheck.PLAYED }
        // Lines naming a printing go first so a plain line doesn't take that printing.
        val order = played.sortedBy { if (it.setCode != null) 0 else 1 }

        val pulled = LinkedHashMap<Long, Int>() // card row id to copies
        val missing = LinkedHashMap<String, Int>()
        var inDeckBox = 0
        for (l in order) {
            var need = l.quantity
            val copies = byName[DeckNames.key(l.name)].orEmpty()
            fun take(c: CollectionCard, pull: Boolean) {
                val n = minOf(need, left[c.id] ?: 0)
                if (n <= 0) return
                left[c.id] = (left[c.id] ?: 0) - n
                need -= n
                if (pull) pulled.merge(c.id, n, Int::plus) else inDeckBox += n
            }
            // Already in the deck box.
            copies.filter { it.boxId in ownBox }
                .sortedBy { if (matchesPrinting(it, l)) 0 else 1 }
                .forEach { if (need > 0) take(it, pull = false) }
            if (need > 0) {
                copies.filter { it.boxId !in ownBox }
                    .sortedWith(
                        compareBy<CollectionCard> { if (matchesPrinting(it, l)) 0 else 1 }
                            .thenBy { it.proxy }
                            .thenBy { boxById[it.boxId]?.kind == "deck" }
                            .thenBy { it.foil }
                            .thenBy { price(it) }
                            .thenBy { it.id },
                    )
                    .forEach { if (need > 0) take(it, pull = true) }
            }
            if (need > 0) {
                val name = copies.firstOrNull()?.name ?: l.name
                missing.merge(name, need, Int::plus)
            }
        }

        val byId = cards.associateBy { it.id }
        val items = pulled.mapNotNull { (id, n) -> byId[id]?.let { it to n } }
            .groupBy { (c, _) -> itemKey(c) }
            .map { (key, cs) ->
                val c = cs.first().first
                c.boxId to PullItem(
                    key = key, name = c.name, qty = cs.sumOf { it.second }, setCode = c.setCode,
                    collectorNumber = c.collectorNumber, foil = c.foil, proxy = c.proxy, imageUrl = c.imageUrl,
                    rows = cs.map { (row, n) -> row.id to n },
                )
            }
        val boxIndex = boxes.mapIndexed { i, b -> b.id to i }.toMap()
        val groups = items.groupBy({ boxById[it.first]?.id }, { it.second })
            .map { (boxId, its) ->
                val box = boxId?.let { boxById[it] }
                PullGroup(
                    boxId = box?.id,
                    boxName = box?.name ?: "Unsorted",
                    items = its.sortedWith(compareBy<PullItem>({ it.name.lowercase() }, { it.setCode }, { it.collectorNumber }, { it.foil })),
                )
            }
            .sortedBy { g -> g.boxId?.let { boxIndex[it] } ?: Int.MAX_VALUE }
        return PullList(
            groups = groups,
            missing = missing.map { (n, q) -> PullMissing(n, q) }.sortedBy { it.name.lowercase() },
            inDeckBox = inDeckBox,
        )
    }
}
