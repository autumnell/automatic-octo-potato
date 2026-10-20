package com.mtgscanner.ui.collection

/**
 * A one-time handoff for opening Browse of the whole collection already sorted or filtered,
 * e.g. from Home's "See all". Set it just before navigating; BrowseViewModel takes it once.
 */
object BrowseLaunch {
    @Volatile var sort: SortBy? = null
    @Volatile var color: String? = null

    /** A smart list being opened: its filters and name. */
    @Volatile var filters: Filters? = null
    @Volatile var title: String? = null

    /** Set when Home can't open Browse itself: Collection opens "All cards" once it shows. */
    @Volatile var openAll: Boolean = false

    /** The starting filters for Browse of the whole collection, then clears the handoff. */
    fun take(): Filters {
        val f = filters ?: Filters(
            sort = sort ?: SortBy.RECENT,
            colors = color?.let { setOf(it) } ?: emptySet(),
        )
        sort = null
        color = null
        filters = null
        return f
    }

    /** The smart list's name for the title, then clears it. */
    fun takeTitle(): String? = title.also { title = null }
}
