package com.mtgscanner.ui.sell

import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.db.CardEvent

/**
 * Takes sold copies out of the collection and notes each sale in the card's history (the Sold list),
 * as the Sell screen does. [items] are (row id, copies, price each). Returns copies sold and the code
 * that undoes it all, for [com.mtgscanner.ui.components.Undo].
 */
suspend fun CollectionRepository.sellCopies(items: List<Triple<Long, Int, Double>>): Pair<Int, suspend () -> Unit> {
    val rows = items.mapNotNull { (id, qty, each) -> cardOnce(id)?.let { Triple(it, qty.coerceIn(1, it.quantity), each) } }
    if (rows.isEmpty()) return 0 to {}
    val before = snapshot(rows.map { it.first.id })
    val logged = mutableListOf<CardEvent>()
    for ((c, n, each) in rows) {
        val event = CardEvent(scryfallId = c.scryfallId, name = c.name, kind = Sales.KIND, detail = Sales.detail(n, each))
        runCatching { dao.addEvent(event) }.getOrNull()?.let { logged += event.copy(id = it) }
        setQuantity(c.id, c.quantity - n)
    }
    val undo: suspend () -> Unit = {
        // The history keeps a note instead of the sale, so it no longer counts toward earned.
        runCatching { dao.putEvents(logged.map { it.copy(kind = "edited", detail = "Sale undone") }) }
        restore(before)
    }
    return rows.sumOf { it.second } to undo
}
