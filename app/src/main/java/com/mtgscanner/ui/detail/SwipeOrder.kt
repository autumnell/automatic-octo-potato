package com.mtgscanner.ui.detail

/**
 * The list a card page was opened from, in the order it showed (a search, Browse sorted by price, Home's movers),
 * so swiping on the card page goes through that list instead of the card's box in name order.
 */
object SwipeOrder {
    @Volatile private var ids: List<Long> = emptyList()

    /** Called by a list just before it opens one of its cards. */
    fun set(order: List<Long>) { ids = order }

    /**
     * The list the card [id] was opened from, or null when it wasn't opened from one (then the box order is used).
     * Read once by the card page it opened, then cleared, so a card opened later from elsewhere doesn't reuse it.
     */
    fun take(id: Long): List<Long>? = ids.takeIf { id in it }.also { ids = emptyList() }
}
