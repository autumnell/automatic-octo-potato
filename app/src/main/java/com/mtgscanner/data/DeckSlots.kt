package com.mtgscanner.data

import com.mtgscanner.data.db.Box
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.Deck
import com.mtgscanner.data.db.facts
import com.mtgscanner.ui.decks.DeckLinkStore
import com.mtgscanner.ui.decks.DeckStatus
import com.mtgscanner.ui.decks.DeckStatusStore
import kotlinx.coroutines.flow.Flow

/*
 * Decks hold cards of their own. A copy kept in a deck sits in a boxes row whose deckId is that deck (its
 * "deck slot"). Those rows are left out of every box query (CardDao.observeBoxes / allBoxes), so box lists,
 * pickers, Auto-sort, re-sort, To sort, box checks, the shelf and labels never see them, and cards whose box
 * isn't among the boxes are skipped by the sorting code. Copies in a deck still count in collection totals.
 */

/** Boxes, then decks' card rows: everywhere a card can be. */
fun CollectionRepository.observePlaces(): Flow<List<Box>> = dao.observePlaces()

suspend fun CollectionRepository.allPlaces(): List<Box> = dao.allPlaces()

/** The row holding [deckId]'s cards, or null when the deck has never held any. */
suspend fun CollectionRepository.deckSlot(deckId: Long): Box? = dao.deckSlot(deckId)

/** The row holding [deckId]'s cards, made the first time it's needed. */
suspend fun CollectionRepository.deckSlotId(deckId: Long): Long {
    dao.deckSlot(deckId)?.let { return it.id }
    val name = dao.deck(deckId)?.name ?: "Deck"
    return dao.insertBox(Box(name = name, kind = "deck", deckId = deckId))
}

/** The box Auto-sort picks for a card, or an Anything box when none fits. */
suspend fun CollectionRepository.autoBoxFor(c: CollectionCard): Long {
    val f = c.facts()
    return pickBox(f, c.oracleId) ?: anythingBox(facts = f)
}

/** Moves [qty] copies of a row to the box Auto-sort picks (out of a deck, usually). */
suspend fun CollectionRepository.moveToAutoBox(rowId: Long, qty: Int) {
    val c = cardOnce(rowId) ?: return
    moveCopies(rowId, qty, autoBoxFor(c))
}

/** What deleting a deck changed, so Undo can bring the deck and its cards back. */
class DeckDeleteUndo internal constructor(
    val deck: Deck,
    internal val slot: CollectionRepository.BoxUndo?,
    internal val link: String? = null,
    internal val status: DeckStatus? = null,
)

/**
 * Deletes a deck. Its cards go to boxes by the Auto-sort rules, or leave the collection with [removeCards].
 * Returns what Undo needs, or null when there's no such deck.
 */
suspend fun CollectionRepository.deleteDeckAndCards(deckId: Long, removeCards: Boolean): DeckDeleteUndo? {
    val deck = dao.deck(deckId) ?: return null
    val slot = dao.deckSlot(deckId)
    val undo = slot?.let { boxUndo(it.id) }
    if (slot != null) deleteBox(slot.id, if (removeCards) null else Box.AUTO_SORT)
    dao.deleteDeck(deckId)
    // The deck's saved link and tag go with it, so nothing is left behind in settings.
    val link = DeckLinkStore.get(appContext, deckId)
    val status = DeckStatusStore.get(appContext, deckId)
    DeckLinkStore.set(appContext, deckId, null)
    DeckStatusStore.set(appContext, deckId, null)
    return DeckDeleteUndo(deck, undo, link, status)
}

/** Brings back a deleted deck with the same id, and its cards as they were. */
suspend fun CollectionRepository.undoDeckDelete(u: DeckDeleteUndo) {
    dao.putDeck(u.deck)
    u.slot?.let { undoBox(it) }
    u.link?.let { DeckLinkStore.set(appContext, u.deck.id, it) }
    u.status?.let { DeckStatusStore.set(appContext, u.deck.id, it) }
}
