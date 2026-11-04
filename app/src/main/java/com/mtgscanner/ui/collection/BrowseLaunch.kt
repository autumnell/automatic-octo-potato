package com.mtgscanner.ui.collection

/**
 * A one-time handoff for opening Browse of the whole collection already sorted or filtered,
 * e.g. from Home's "See all". Set it just before navigating; BrowseViewModel takes it once.
 */
object BrowseLaunch {
    @Volatile var sort: SortBy? = null
    @Volatile var color: String? = null

    /** A smart list being opened: its filters and name, and the box it was saved in (null = whole collection). */
    @Volatile var filters: Filters? = null
    @Volatile var title: String? = null
    @Volatile var boxId: Long? = null

    /** Set by the Collection tab's search bar: Browse opens with the cursor in its search field. */
    @Volatile var focusSearch: Boolean = false

    fun takeFocus(): Boolean = focusSearch.also { focusSearch = false }

    /** Opens a smart list: call just before navigating to Browse of [SavedSearch.boxId] (or everything). */
    fun open(s: SavedSearch) {
        filters = s.filters
        title = s.name
        boxId = s.boxId
    }

    /** Whether a smart list is waiting for Browse of [box] (-1 = the whole collection). */
    fun pendingFor(box: Long): Boolean = filters != null && (boxId ?: -1L) == box

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
        boxId = null
        return f
    }

    /** The smart list's name for the title, then clears it. */
    fun takeTitle(): String? = title.also { title = null }
}

/** A one-time handoff for opening a set's page on its Missing list (from Home's new set offer). */
object SetLaunch {
    @Volatile var missing: Boolean = false

    /** Whether to start on Missing; clears the handoff. */
    fun takeMissing(): Boolean = missing.also { missing = false }
}
