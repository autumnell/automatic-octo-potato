package com.mtgscanner.ui.collection

import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.data.db.WishlistItem

/** What a bulk wishlist add did: how many cards it covered, and how to put the wishlist back. */
class WishlistBatch(val count: Int, val changed: Int, val undo: suspend () -> Unit)

/**
 * Wants at least the given copies of each card ([CollectionRepository.wantAtLeast]), remembering each
 * wishlist row as it was first, so Undo removes new rows and puts raised counts back.
 */
suspend fun CollectionRepository.wantAllWithUndo(wants: List<Pair<ScryfallCard, Int>>): WishlistBatch {
    // Oracle id to its row before this batch (null: wasn't on the wishlist). First sight wins.
    val before = LinkedHashMap<String, WishlistItem?>()
    var count = 0
    for ((card, n) in wants) {
        val oracle = card.oracleId ?: continue
        val prev = if (before.containsKey(oracle)) null else wishlistItem(oracle)
        val ok = runCatching { wantAtLeast(card, n) }.isSuccess
        if (!ok) continue
        count++
        if (!before.containsKey(oracle) && n > 0 && (prev == null || prev.wanted < n)) before[oracle] = prev
    }
    val undo: suspend () -> Unit = {
        for ((oracle, prev) in before) {
            if (prev == null) wishlistItem(oracle)?.let { removeWish(it.id) } else restoreWish(prev)
        }
    }
    return WishlistBatch(count, before.size, undo)
}
