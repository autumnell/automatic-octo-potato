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

/**
 * Records the language read off a scanned card. Only for a row that holds just the copies scanned now,
 * so copies already there keep theirs. (Passing the language to [CollectionRepository.add] keeps it from the start.)
 */
suspend fun CollectionRepository.setScanLang(rowId: Long, qty: Int, lang: String): Boolean {
    val c = dao.get(rowId) ?: return false
    if (c.lang == lang || c.quantity != qty) return false
    update(c.copy(lang = lang))
    return true
}

/** Rows holding copies of a card, any printing (by oracle id, else by name). Read from the shared card list. */
suspend fun CollectionRepository.copiesOf(oracleId: String?, name: String): List<com.mtgscanner.data.db.CollectionCard> =
    observeAll().first().filter { if (oracleId != null) it.oracleId == oracleId else it.name.equals(name, ignoreCase = true) }

/** Where copies of a card are kept: box id to copies, most first. */
suspend fun CollectionRepository.keptIn(oracleId: String?): List<Pair<Long, Int>> {
    if (oracleId == null) return emptyList()
    return copiesOf(oracleId, "").groupBy { it.boxId }.map { (box, rows) -> box to rows.sumOf { it.quantity } }.sortedByDescending { it.second }
}

/**
 * The row to take one copy of [card] from when it's scanned out: the same printing and finish first, then the
 * same printing, then any printing of the card. Null when none is owned.
 */
suspend fun CollectionRepository.rowToRemove(card: ScryfallCard, foil: Boolean): com.mtgscanner.data.db.CollectionCard? {
    val rows = copiesOf(card.oracleId, card.name).filter { it.quantity > 0 }
    return rows.firstOrNull { it.scryfallId == card.id && it.foil == foil }
        ?: rows.firstOrNull { it.scryfallId == card.id }
        ?: rows.maxByOrNull { it.quantity }
}

/** Other boxes whose rules a card also fits, after [chosen], in Auto-sort order. Boxes that take any card are left out. */
suspend fun CollectionRepository.alsoFits(facts: CardFacts, chosen: Long, limit: Int = 3): List<Long> =
    boxInfos().filter { it.id != chosen && !it.rules.isOpen && it.rules.matches(facts) }.take(limit).map { it.id }
