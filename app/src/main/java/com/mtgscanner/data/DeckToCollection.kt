package com.mtgscanner.data

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
    val r = BackupIO.applyPlan(this, plan)
    val undo: suspend () -> Unit = { undoImport(r) }
    return DeckAddResult(r.added, r.notFound.size, if (r.canUndo) undo else null)
}
