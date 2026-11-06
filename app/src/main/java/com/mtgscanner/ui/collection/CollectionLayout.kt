package com.mtgscanner.ui.collection

/**
 * A block of the Boxes page that can be shown, hidden and moved. "browse" was one until Browse all moved above
 * the tabs ([CollectionPart.BROWSE]); saved orders that still name it just drop it.
 */
enum class CollectionSection(val key: String, val label: String) {
    RING("ring", "Value ring"),
    TO_SORT("sort", "To sort"),
    BOXES("boxes", "Boxes"),
    LISTS("lists", "Lists"),
    ;

    companion object {
        fun of(key: String): CollectionSection? = entries.firstOrNull { it.key == key }
    }
}

/** A smaller part of the Collection tab that can be turned off. Saved as the hidden ones, so new parts start shown. */
enum class CollectionPart(val key: String, val label: String, val group: Group) {
    /** The page search above the tabs: boxes, decks and cards. Its key stays "browseAll" so saved layouts keep it. */
    BROWSE("browseAll", "Search", Group.TOP),
    /** Cards, value and 7-day change in a strip under the title. */
    STATS("stats", "Stats", Group.TOP),
    LEGEND("legend", "Ring legend", Group.PAGE),
    BOX_TOOLS("boxTools", "Shelf & order", Group.PAGE),
    ART("art", "Cover art", Group.BOX),
    RULES("rules", "Rules", Group.BOX),
    COUNT("count", "Card count", Group.BOX),
    VALUE("value", "Value", Group.BOX),
    CHANGE("change", "Value change", Group.BOX),
    FILL("fill", "Fill meter", Group.BOX),
    DECKS("decks", "Decks", Group.TABS),
    WISHLIST("wishlist", "Wishlist", Group.TABS),
    TOOLS("tools", "Tools", Group.TABS),
    TRADE("trade", "Trade binders", Group.TABS),
    ;

    enum class Group { TOP, PAGE, BOX, TABS }

    companion object {
        fun of(key: String): CollectionPart? = entries.firstOrNull { it.key == key }
    }
}

/** What the Collection tab shows. The default is everything, as before it could be changed. */
data class CollectionLayout(
    /** Shown Boxes-page sections, in order. */
    val sections: List<CollectionSection> = CollectionSection.entries,
    val hidden: Set<CollectionPart> = emptySet(),
    /** One-line box cards: name, count and value. */
    val compact: Boolean = false,
) {
    fun shows(p: CollectionPart) = p !in hidden

    companion object {
        val DEFAULT = CollectionLayout()

        /**
         * [saved] is the shown section order (null when never saved), [known] the sections that existed then:
         * sections added since show up at the end. Unknown keys are dropped.
         */
        fun sections(saved: String?, known: String?): List<CollectionSection> {
            if (saved == null) return CollectionSection.entries
            val shown = saved.split(',').mapNotNull { CollectionSection.of(it.trim()) }.distinct()
            val knownKeys = known.orEmpty().split(',').map { it.trim() }.toSet()
            return shown + CollectionSection.entries.filter { it.key !in knownKeys && it !in shown }
        }

        /** Hidden parts from what was saved; keys from older or newer versions that no longer exist are dropped. */
        fun hidden(saved: String?): Set<CollectionPart> =
            saved.orEmpty().split(',').mapNotNull { CollectionPart.of(it.trim()) }.toSet()

        fun load(sections: String?, known: String?, hidden: String?, compact: Boolean) =
            CollectionLayout(sections(sections, known), hidden(hidden), compact)

        fun sectionsKey(l: List<CollectionSection>) = l.distinct().joinToString(",") { it.key }
        fun knownKey() = CollectionSection.entries.joinToString(",") { it.key }
        fun hiddenKey(s: Set<CollectionPart>) = CollectionPart.entries.filter { it in s }.joinToString(",") { it.key }
    }
}

/** The sub-tabs of the Collection tab; Boxes is always there. */
enum class CollectionTab(val label: String, val part: CollectionPart?) {
    BOXES("Boxes", null),
    /** Every card in the collection, as in Browse. */
    ALL("All", null),
    DECKS("Decks", CollectionPart.DECKS),
    WISHLIST("Wishlist", CollectionPart.WISHLIST),
    TOOLS("Tools", CollectionPart.TOOLS),
    ;

    companion object {
        fun shown(l: CollectionLayout): List<CollectionTab> = entries.filter { t -> t.part.let { it == null || l.shows(it) } }
    }
}
