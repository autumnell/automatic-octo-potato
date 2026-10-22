package com.mtgscanner.data

/** Where a deck line's owned copies are, for the deck page. Pure, for tests. */
object DeckPlaces {
    /** A box ([deckId] 0) or a deck's card row. */
    data class Place(val name: String, val deckId: Long = 0)

    /** [boxes]: this deck first ("In deck"), then boxes by name; [otherDecks]: how many other decks hold copies. */
    data class Where(val boxes: List<Pair<String, Int>>, val otherDecks: Int)

    /**
     * Sums [copies] (place id, count) by place. Copies in [ownSlots] (this deck's rows) are "In deck"; copies in
     * other decks are only counted as decks, so the line stays short. Unknown places read as "Unsorted".
     */
    fun where(copies: List<Pair<Long?, Int>>, places: Map<Long, Place>, ownSlots: Set<Long>): Where {
        var inDeck = 0
        val boxes = LinkedHashMap<String, Int>()
        val decks = HashSet<Long>()
        for ((id, n) in copies) {
            if (n <= 0) continue
            val p = id?.let { places[it] }
            when {
                id != null && id in ownSlots -> inDeck += n
                p != null && p.deckId != 0L -> decks += p.deckId
                else -> { val name = p?.name ?: "Unsorted"; boxes[name] = (boxes[name] ?: 0) + n }
            }
        }
        val list = buildList {
            if (inDeck > 0) add(DeckCheckLine.IN_DECK to inDeck)
            addAll(boxes.toList())
        }
        return Where(list, decks.size)
    }

    /** "In deck ×1 · Binder ×2 · 2 other decks". */
    fun text(boxes: List<Pair<String, Int>>, otherDecks: Int): String = buildList {
        boxes.forEach { (b, n) -> add("$b ×$n") }
        if (otherDecks == 1) add("1 other deck") else if (otherDecks > 1) add("$otherDecks other decks")
    }.joinToString(" · ")
}
