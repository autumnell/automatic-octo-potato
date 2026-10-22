package com.mtgscanner.data

import androidx.room.withTransaction
import com.mtgscanner.util.ImportRow

/** What adding a deck list did, and how to take it back. */
class DeckAddResult(val added: Int, val notFound: Int, val undo: (suspend () -> Unit)?)

/**
 * Adds [rows] to the collection, kept in deck [deckId] (see DeckSlots.kt), in one transaction through the import
 * path: the printing each row names (see [PrintingLookup]), foil when marked and the printing has it. Copies
 * already in boxes stay where they are. The result's undo takes off exactly the copies added.
 * Throws IOException when Scryfall can't be reached.
 */
suspend fun CollectionRepository.addDeckRows(rows: List<ImportRow>, deckId: Long): DeckAddResult {
    if (rows.isEmpty()) return DeckAddResult(0, 0, null)
    val found = BackupIO.lookUp(this, rows)
    val fixed = rows.mapIndexed { i, r ->
        val c = found.getOrNull(i) ?: return@mapIndexed r
        r.copy(foil = r.foil && c.hasFoil || !c.hasNonFoil)
    }
    val slot = deckSlotId(deckId)
    val plan = ImportPlan(fixed, found, found.map { c -> if (c == null) null else slot }, slot)
    val ids = found.filterNotNull().map { it.id }.distinct()
    val before = ids.chunked(500).flatMap { dao.byScryfallIds(it) }.associate { it.id to it.quantity }
    val r = BackupIO.applyPlan(this, plan)
    val after = ids.chunked(500).flatMap { dao.byScryfallIds(it) }
    val added = after.mapNotNull { c -> (c.quantity - (before[c.id] ?: 0)).takeIf { it > 0 }?.let { c.id to it } }
    val undo: suspend () -> Unit = {
        db.withTransaction { added.forEach { (id, n) -> remove(id, n) } }
    }
    return DeckAddResult(r.added, r.notFound.size, if (added.isEmpty()) null else undo)
}
