package com.mtgscanner.ui.sell

import com.mtgscanner.data.db.CollectionCard

/** One line of a buylist pull list: copies to pull from a row, and what the store pays for each. */
data class PullItem(val card: CollectionCard, val copies: Int, val each: Double) {
    val total: Double get() = copies * each
}

/** Works out what's worth selling to a buylist. Pure Kotlin, so it can be tested without a phone. */
object BuylistMath {
    /**
     * Every owned row the store pays at least [min] a copy for, in the order the boxes are pulled from
     * ([boxOrder], then binder slot, then name). Proxies are left out, and so are copies lent out ([outOf]).
     */
    fun pullList(
        cards: List<CollectionCard>,
        min: Double,
        boxOrder: List<Long>,
        priceOf: (CollectionCard) -> Double?,
        outOf: (CollectionCard) -> Int = { 0 },
    ): List<PullItem> {
        val rank = boxOrder.withIndex().associate { (i, id) -> id to i }
        return cards.asSequence()
            .filter { !it.proxy }
            .mapNotNull { c ->
                val each = priceOf(c)?.takeIf { it >= min && it > 0 } ?: return@mapNotNull null
                val copies = c.quantity - outOf(c).coerceAtLeast(0)
                if (copies <= 0) null else PullItem(c, copies, each)
            }
            .sortedWith(
                compareBy<PullItem>({ rank[it.card.boxId] ?: Int.MAX_VALUE }, { if (it.card.binderSlot > 0) it.card.binderSlot else Int.MAX_VALUE })
                    .thenBy { it.card.name.lowercase() },
            )
            .toList()
    }

    /** "Page 3" for a card in a 9-pocket binder slot, or null when it isn't placed. */
    fun pageOf(slot: Int): String? = if (slot > 0) "Page ${(slot - 1) / 9 + 1}" else null
}
