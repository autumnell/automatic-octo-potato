package com.mtgscanner.ui.decks

/** The card types a deck's picture view groups by, in the order the groups are shown. */
enum class DeckType(val one: String, val many: String) {
    CREATURE("Creature", "Creatures"),
    PLANESWALKER("Planeswalker", "Planeswalkers"),
    BATTLE("Battle", "Battles"),
    INSTANT("Instant", "Instants"),
    SORCERY("Sorcery", "Sorceries"),
    ARTIFACT("Artifact", "Artifacts"),
    ENCHANTMENT("Enchantment", "Enchantments"),
    LAND("Land", "Lands"),
    OTHER("Other", "Other"),
}

object DeckTypes {
    /**
     * The group for a type line. A card with several types goes in the first group of [DeckType]'s order, so an
     * artifact creature is a creature and a land creature is a creature too. Only the front face counts.
     */
    fun of(typeLine: String?): DeckType {
        val front = typeLine?.substringBefore("//")?.substringBefore("—")?.lowercase() ?: return DeckType.OTHER
        val words = front.split(' ', '-').filter { it.isNotBlank() }.toSet()
        return DeckType.entries.firstOrNull { it != DeckType.OTHER && it.one.lowercase() in words } ?: DeckType.OTHER
    }

    /** [items] in their type groups, in [DeckType] order, empty groups left out; each group keeps [items]' order. */
    fun <T> group(items: List<T>, typeLine: (T) -> String?): List<Pair<DeckType, List<T>>> {
        val byType = items.groupBy { of(typeLine(it)) }
        return DeckType.entries.mapNotNull { t -> byType[t]?.let { t to it } }
    }

    /**
     * Splits [items] into [columns] piles, top to bottom then left to right, as even as possible (the first piles
     * get one more when it doesn't divide).
     */
    fun <T> piles(items: List<T>, columns: Int): List<List<T>> {
        if (columns <= 0) return emptyList()
        val base = items.size / columns
        val extra = items.size % columns
        var at = 0
        return List(columns) { i ->
            val n = base + if (i < extra) 1 else 0
            items.subList(at, at + n).also { at += n }
        }
    }
}
