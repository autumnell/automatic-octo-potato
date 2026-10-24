package com.mtgscanner.data

import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.Deck
import com.mtgscanner.util.CardNames
import com.mtgscanner.util.DeckLine
import com.mtgscanner.util.DecklistParser
import java.util.concurrent.ConcurrentHashMap

/** Copies owned by card name, keyed by [CardNames.key] so two-faced and accented names match deck lines. */
object OwnedNames {
    fun of(cards: List<CollectionCard>): Map<String, Int> {
        val out = HashMap<String, Int>()
        for (c in cards) {
            val k = CardNames.key(c.name)
            out[k] = (out[k] ?: 0) + c.quantity
        }
        return out
    }
}

/**
 * Parsed deck lists by deck id, reused until the deck's text changes. The Decks tab, Home, the wishlist and
 * the deck page all read every deck on each collection change; this keeps that from reparsing them all.
 */
object ParsedDecks {
    private val cache = ConcurrentHashMap<Long, Pair<String, List<DeckLine>>>()

    fun of(d: Deck): List<DeckLine> =
        cache[d.id]?.takeIf { it.first == d.text }?.second
            ?: DecklistParser.parse(d.text).also { cache[d.id] = d.text to it }
}
