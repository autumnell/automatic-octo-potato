package com.mtgscanner.data

import com.mtgscanner.data.db.Deck
import com.mtgscanner.scan.DeckText
import kotlinx.coroutines.flow.first

// Repository helpers for the scanner's extra modes: price check, scan to a deck, language and rule preview.

/** Copies of a card you own, any printing, in any box. */
suspend fun CollectionRepository.ownedCopies(oracleId: String?): Int =
    if (oracleId == null) 0 else dao.ownedOutside(oracleId, listOf(Long.MIN_VALUE))

suspend fun CollectionRepository.deckById(id: Long): Deck? = dao.deck(id)

/** Adds [delta] copies of [name] to a deck list's main deck (negative takes them off). Returns the copies now listed. */
suspend fun CollectionRepository.addToDeckList(deckId: Long, name: String, delta: Int): Int? {
    val deck = deckById(deckId) ?: return null
    val text = DeckText.adjust(deck.text, name, delta)
    dao.putDeck(deck.copy(text = text, updatedAt = System.currentTimeMillis()))
    return DeckText.count(text, name)
}

/** Rows holding copies of a card, any printing (by oracle id, else by name), read fresh from the database. */
suspend fun CollectionRepository.copiesOf(oracleId: String?, name: String): List<com.mtgscanner.data.db.CollectionCard> =
    if (oracleId != null) dao.byOracleId(oracleId) else dao.byName(name)

/** Where copies of a card are kept: box id to copies, most first. */
suspend fun CollectionRepository.keptIn(oracleId: String?): List<Pair<Long, Int>> {
    if (oracleId == null) return emptyList()
    return copiesOf(oracleId, "").groupBy { it.boxId }.map { (box, rows) -> box to rows.sumOf { it.quantity } }.sortedByDescending { it.second }
}

/**
 * The row to take one copy of [card] from when it's scanned out: the same printing and finish first, then the
 * same printing, then any printing of the card. Copies in boxes come before copies kept in a deck. Null when none is owned.
 */
suspend fun CollectionRepository.rowToRemove(card: ScryfallCard, foil: Boolean): com.mtgscanner.data.db.CollectionCard? {
    val owned = copiesOf(card.oracleId, card.name).filter { it.quantity > 0 }
    // allBoxes() leaves decks' rows out; allPlaces() has them.
    val deckSlots = dao.allPlaces().filter { it.deckId != 0L }.mapTo(HashSet()) { it.id }
    val rows = owned.filter { it.boxId !in deckSlots }.ifEmpty { owned }
    return rows.firstOrNull { it.scryfallId == card.id && it.foil == foil }
        ?: rows.firstOrNull { it.scryfallId == card.id }
        ?: rows.maxByOrNull { it.quantity }
}

/** Other boxes whose rules a card also fits, after [chosen], in Auto-sort order. Boxes that take any card are left out. */
suspend fun CollectionRepository.alsoFits(facts: CardFacts, chosen: Long, limit: Int = 3): List<Long> =
    boxInfos().filter { it.id != chosen && !it.rules.isOpen && it.rules.matches(facts) }.take(limit).map { it.id }
