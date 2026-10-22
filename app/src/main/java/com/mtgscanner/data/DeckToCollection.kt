package com.mtgscanner.data

import androidx.room.withTransaction
import com.mtgscanner.util.ImportRow

/** What adding a deck list did, and how to take it back. */
class DeckAddResult(val added: Int, val notFound: Int, val undo: (suspend () -> Unit)?)

/**
 * Adds [rows] to the collection through the import path, in one transaction: the printing each row names
 * (see [PrintingLookup]), foil when marked and the printing has it, into [boxId] or by each box's rules
 * when it's [com.mtgscanner.data.db.Box.AUTO_SORT]. Cards no box fits go to an Anything box. The result's
 * undo takes off exactly the copies added and drops boxes made for them that are still empty.
 * Throws IOException when Scryfall can't be reached.
 */
suspend fun CollectionRepository.addDeckRows(rows: List<ImportRow>, boxId: Long): DeckAddResult {
    if (rows.isEmpty()) return DeckAddResult(0, 0, null)
    val found = BackupIO.lookUp(this, rows)
    val fixed = rows.mapIndexed { i, r ->
        val c = found.getOrNull(i) ?: return@mapIndexed r
        r.copy(foil = r.foil && c.hasFoil || !c.hasNonFoil)
    }
    val plan = BackupIO.plan(this, fixed, found, boxId)
    val ids = found.filterNotNull().map { it.id }.distinct()
    val boxesBefore = dao.allBoxes().map { it.id }.toSet()
    val before = ids.chunked(500).flatMap { dao.byScryfallIds(it) }.associate { it.id to it.quantity }
    val fallback: suspend () -> Long = { anythingBox() }
    val r = BackupIO.applyPlan(this, plan, fallback)
    val after = ids.chunked(500).flatMap { dao.byScryfallIds(it) }
    val added = after.mapNotNull { c -> (c.quantity - (before[c.id] ?: 0)).takeIf { it > 0 }?.let { c.id to it } }
    val newBoxes = dao.allBoxes().map { it.id }.filter { it !in boxesBefore }
    val undo: suspend () -> Unit = {
        db.withTransaction { added.forEach { (id, n) -> remove(id, n) } }
        newBoxes.forEach { b -> if (dao.inBox(b).isEmpty()) deleteBox(b, null) }
    }
    return DeckAddResult(r.added, r.notFound.size, if (added.isEmpty()) null else undo)
}
