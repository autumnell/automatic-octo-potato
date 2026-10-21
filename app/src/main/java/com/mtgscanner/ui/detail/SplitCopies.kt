package com.mtgscanner.ui.detail

import androidx.room.withTransaction
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.mergedWith

/** What "Split copies" changes for some copies of a row: their condition, finish and language. */
data class SplitChange(val qty: Int, val condition: String, val foil: Boolean, val lang: String)

/** Pure planning for [splitCopies], so it can be tested without a database. */
object SplitMath {
    /** True when [change] actually changes something on [row] and leaves at least one copy behind. */
    fun valid(row: CollectionCard, change: SplitChange): Boolean =
        change.qty in 1 until row.quantity &&
            (change.condition != row.condition || change.foil != row.foil || !change.lang.equals(row.lang, ignoreCase = true))

    /** The copies split off as a new row (id 0), before any merge with an existing twin. */
    fun splitRow(row: CollectionCard, change: SplitChange, now: Long): CollectionCard = row.copy(
        id = 0,
        quantity = change.qty,
        condition = change.condition,
        foil = change.foil,
        lang = change.lang,
        // A new condition or finish needs its own condition price, and a binder pocket of its own.
        conditionPrice = null,
        conditionPriceAt = 0,
        binderSlot = 0,
        // The photo stays with the copies it shows.
        photo = "",
        updatedAt = now,
    )

    /** "1 of 4 is LP" style summary for the Undo message. */
    fun message(row: CollectionCard, change: SplitChange): String {
        val parts = buildList {
            if (change.condition != row.condition) add(change.condition)
            if (change.foil != row.foil) add(if (change.foil) "foil" else "non-foil")
            if (!change.lang.equals(row.lang, ignoreCase = true)) add(change.lang.uppercase())
        }
        val verb = if (change.qty == 1) "is" else "are"
        return "${change.qty} of ${row.quantity} $verb ${parts.joinToString(", ")}"
    }
}

/**
 * Splits [change].qty copies off row [rowId] with a new condition, finish or language. They join an
 * identical row in the same box when there is one, else get a row of their own. Returns the id of the
 * row the split copies are in, or null when nothing changed.
 */
suspend fun CollectionRepository.splitCopies(rowId: Long, change: SplitChange): Long? {
    val row = dao.get(rowId) ?: return null
    if (!SplitMath.valid(row, change)) return null
    val now = System.currentTimeMillis()
    val split = SplitMath.splitRow(row, change, now)
    val id = db.withTransaction {
        dao.update(row.copy(quantity = row.quantity - change.qty, updatedAt = now))
        val twin = twinOf(split)?.takeIf { it.id != row.id }
        if (twin != null) {
            dao.update(twin.mergedWith(split, now = now, details = false))
            twin.id
        } else {
            dao.insert(split)
        }
    }
    logEvent(row.scryfallId, row.name, "edited", SplitMath.message(row, change))
    return id
}
