package com.mtgscanner.data

import com.mtgscanner.data.db.WishlistItem

/** Wishlist rows for [oracleIds] as they are now (missing ones left out), so a change to them can be undone. */
suspend fun CollectionRepository.wishSnapshot(oracleIds: Collection<String>): Map<String, WishlistItem> =
    oracleIds.distinct().mapNotNull { o -> wishlistItem(o)?.let { o to it } }.toMap()

/** Undoes wishlist adds: rows in [before] go back as they were, rows added since are removed. */
suspend fun CollectionRepository.undoWishes(oracleIds: Collection<String>, before: Map<String, WishlistItem>) {
    oracleIds.distinct().forEach { o ->
        val was = before[o]
        if (was != null) restoreWish(was) else wishlistItem(o)?.let { removeWish(it.id) }
    }
}

/** What a bulk wishlist add did: how many cards it covered, how many rows it changed, and how to put them back. */
class WishlistBatch(val count: Int, val changed: Int, val undo: suspend () -> Unit)

/**
 * Wants at least the given copies of each card ([CollectionRepository.wantAtLeast]), remembering each
 * wishlist row as it was first, so Undo removes new rows and puts raised counts back.
 */
suspend fun CollectionRepository.wantAllWithUndo(wants: List<Pair<ScryfallCard, Int>>): WishlistBatch {
    // Only rows this batch raises or adds are undone.
    val touched = LinkedHashSet<String>()
    val before = wishSnapshot(wants.mapNotNull { it.first.oracleId })
    var count = 0
    for ((card, n) in wants) {
        val oracle = card.oracleId ?: continue
        if (runCatching { wantAtLeast(card, n) }.isFailure) continue
        count++
        val was = before[oracle]
        if (n > 0 && (was == null || was.wanted < n)) touched += oracle
    }
    return WishlistBatch(count, touched.size) { undoWishes(touched, before) }
}

/** The cheapest paper printing of [card] with a USD price, or [card] when there's none or Scryfall can't be reached. */
suspend fun CollectionRepository.cheapestPrinting(card: ScryfallCard): ScryfallCard {
    val prints = runCatching { api.search(exactNameQuery(card.name) + " game:paper", unique = "prints", order = "usd") }.getOrDefault(emptyList())
    return prints.filter { it.oracleId == card.oracleId }
        .minByOrNull { it.prices.usd?.toDoubleOrNull() ?: Double.MAX_VALUE }
        ?.takeIf { it.prices.usd?.toDoubleOrNull() != null } ?: card
}
