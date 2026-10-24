package com.mtgscanner.ui.collection

/** How the wishlist is ordered. */
enum class WishlistSort(val label: String) {
    NAME("Name"),
    PRICE("Price"),
    ADDED("Added"),
    DECK("Deck"),
}

/** Wishlist search and sort; pure (works on any row through selectors) so it can be tested. */
object WishlistSorting {
    /**
     * Rows whose name contains [query] (any case), in [sort] order: names A to Z, prices highest first
     * (no price last), or newest first. Ties go by name.
     */
    fun <T> apply(
        items: List<T>,
        query: String,
        sort: WishlistSort,
        name: (T) -> String,
        price: (T) -> Double?,
        added: (T) -> Long,
    ): List<T> {
        val q = query.trim()
        val shown = if (q.isEmpty()) items else items.filter { name(it).contains(q, ignoreCase = true) }
        val byName = compareBy<T> { name(it).lowercase() }
        return when (sort) {
            WishlistSort.NAME, WishlistSort.DECK -> shown.sortedWith(byName)
            WishlistSort.PRICE -> shown.sortedWith(compareBy<T> { price(it) == null }.thenByDescending { price(it) ?: 0.0 }.then(byName))
            WishlistSort.ADDED -> shown.sortedWith(compareByDescending<T> { added(it) }.then(byName))
        }
    }
}
