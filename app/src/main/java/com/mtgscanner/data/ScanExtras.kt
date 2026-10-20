package com.mtgscanner.data

import com.mtgscanner.data.db.Deck
import com.mtgscanner.scan.DeckText

// Repository helpers for the scanner's extra modes: price check, scan to a deck, language and rule preview.

/** Copies of a card you own, any printing, in any box. */
suspend fun CollectionRepository.ownedCopies(oracleId: String?): Int =
    if (oracleId == null) 0 else dao.ownedOutside(oracleId, listOf(Long.MIN_VALUE))

suspend fun CollectionRepository.deckById(id: Long): Deck? = dao.allDecks().firstOrNull { it.id == id }

/** Adds [delta] copies of [name] to a deck list's main deck (negative takes them off). Returns the copies now listed. */
suspend fun CollectionRepository.addToDeckList(deckId: Long, name: String, delta: Int): Int? {
    val deck = deckById(deckId) ?: return null
    val text = DeckText.adjust(deck.text, name, delta)
    dao.putDeck(deck.copy(text = text, updatedAt = System.currentTimeMillis()))
    return DeckText.count(text, name)
}

/**
 * Records the language read off a scanned card. Only for a row that holds just the copies scanned now:
 * a row can't hold two languages, so copies that merged into an existing row keep its language.
 */
suspend fun CollectionRepository.setScanLang(rowId: Long, qty: Int, lang: String): Boolean {
    val c = dao.get(rowId) ?: return false
    if (c.lang == lang || c.quantity != qty) return false
    update(c.copy(lang = lang))
    return true
}

/** Other boxes whose rules a card also fits, after [chosen], in Auto-sort order. Boxes that take any card are left out. */
suspend fun CollectionRepository.alsoFits(facts: CardFacts, chosen: Long, limit: Int = 3): List<Long> =
    boxInfos().filter { it.id != chosen && !it.rules.isCatchAll && it.rules.matches(facts) }.take(limit).map { it.id }
