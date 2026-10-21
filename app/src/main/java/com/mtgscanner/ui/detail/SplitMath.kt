package com.mtgscanner.ui.detail

import com.mtgscanner.data.db.CollectionCard

/** What "Split copies" changes for some copies of a row: their condition, finish and language. */
data class SplitChange(val qty: Int, val condition: String, val foil: Boolean, val lang: String)

/** Pure planning for [splitCopies], so it can be tested without a database. */
object SplitMath {
    /** True when [change] actually changes something on [row] and leaves at least one copy behind. */
    fun valid(row: CollectionCard, change: SplitChange): Boolean =
        change.qty in 1 until row.quantity &&
            (change.condition != row.condition || change.foil != row.foil || !change.lang.equals(row.lang.ifBlank { "en" }, ignoreCase = true))

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
            if (!change.lang.equals(row.lang.ifBlank { "en" }, ignoreCase = true)) add(change.lang.uppercase())
        }
        val verb = if (change.qty == 1) "is" else "are"
        return "${change.qty} of ${row.quantity} $verb ${parts.joinToString(", ")}"
    }
}
