package com.mtgscanner.ui.search

import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.db.WishlistItem

/** Wishlist rows for [oracleIds] as they are now (missing ones left out), so a change to them can be undone. */
internal suspend fun CollectionRepository.wishSnapshot(oracleIds: Collection<String>): Map<String, WishlistItem> =
    oracleIds.distinct().mapNotNull { o -> wishlistItem(o)?.let { o to it } }.toMap()

/** Undoes wishlist adds: rows in [before] go back as they were, rows added since are removed. */
internal suspend fun CollectionRepository.undoWishes(oracleIds: Collection<String>, before: Map<String, WishlistItem>) {
    oracleIds.distinct().forEach { o ->
        val was = before[o]
        if (was != null) restoreWish(was) else wishlistItem(o)?.let { removeWish(it.id) }
    }
}
