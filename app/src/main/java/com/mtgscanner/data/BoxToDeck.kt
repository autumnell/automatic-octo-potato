package com.mtgscanner.data

import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.util.CardNames
import com.mtgscanner.util.DecklistParser

/** Moving a whole box into a deck. Pure parts, for tests. */
object BoxToDeckMath {
    /** A decklist of [cards]: one line per printing, "2 Name (SET) 123", A to Z. Proxies count like any card. */
    fun list(cards: List<CollectionCard>): String =
        cards.filter { it.quantity > 0 }
            .groupBy { Triple(CardNames.key(it.name), it.setCode.lowercase(), it.collectorNumber) }
            .values.sortedBy { it.first().name.lowercase() }
            .joinToString("\n") { rows ->
                val c = rows.first()
                "${rows.sumOf { it.quantity }} ${c.name} (${c.setCode.uppercase()}) ${c.collectorNumber}"
            }

    /** [text] with lines added for cards in [cards] that it doesn't list yet, in the main deck (never under a Sideboard header). */
    fun addMissing(text: String, cards: List<CollectionCard>): String {
        val listed = DecklistParser.parse(text).map { CardNames.key(it.name) }.toSet()
        val extra = list(cards.filter { CardNames.key(it.name) !in listed })
        return if (extra.isEmpty()) text else DecklistParser.addToMain(text, extra)
    }
}

/**
 * Moves every card in box [boxId] into a deck: [deckId], or a new deck named after the box when it's 0.
 * Cards the deck's list doesn't name are added to it. Returns the deck id and an Undo, or null when the box is empty.
 */
suspend fun CollectionRepository.boxToDeck(boxId: Long, deckId: Long, newName: String): Pair<Long, suspend () -> Unit>? {
    val cards = cardsInBox(boxId).filter { it.quantity > 0 }
    if (cards.isEmpty()) return null
    val old = if (deckId != 0L) dao.deck(deckId) else null
    val id = if (old != null) {
        saveDeck(old.id, old.name, BoxToDeckMath.addMissing(old.text, cards))
    } else {
        saveDeck(0L, newName.ifBlank { "Untitled deck" }, BoxToDeckMath.list(cards))
    }
    val slot = deckSlotId(id)
    val before = snapshot(cards.map { it.id })
    moveCopiesMany(cards.map { it.id to it.quantity }, slot)
    val undo: suspend () -> Unit = {
        restore(before)
        if (old != null) dao.putDeck(old) else deleteDeckAndCards(id, removeCards = false)
    }
    return id to undo
}
