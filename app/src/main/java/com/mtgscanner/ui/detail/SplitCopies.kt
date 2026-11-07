package com.mtgscanner.ui.detail

import androidx.room.withTransaction
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.mergedWith

/**
 * Splits [change].qty copies off row [rowId] with a new condition, finish or language. They join an
 * identical row in the same box when there is one, else get a row of their own. Returns the id of the
 * row the split copies are in, or null when nothing changed.
 */
suspend fun CollectionRepository.splitCopies(rowId: Long, change: SplitChange): Long? {
    val now = System.currentTimeMillis()
    // Read and written in one transaction, so a count changed meanwhile isn't overwritten.
    val (row, id) = db.withTransaction {
        val row = dao.get(rowId) ?: return@withTransaction null
        if (!SplitMath.valid(row, change)) return@withTransaction null
        val split = SplitMath.splitRow(row, change, now)
        dao.update(row.copy(quantity = row.quantity - change.qty, updatedAt = now))
        val twin = twinOf(split)?.takeIf { it.id != row.id }
        row to if (twin != null) {
            dao.update(twin.mergedWith(split, now = now, details = false))
            twin.id
        } else {
            dao.insert(split)
        }
    } ?: return null
    afterChange()
    logEvent(row.scryfallId, row.name, "edited", SplitMath.message(row, change))
    return id
}
