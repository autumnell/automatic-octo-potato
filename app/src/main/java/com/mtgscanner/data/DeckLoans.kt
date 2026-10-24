package com.mtgscanner.data

import androidx.room.withTransaction
import com.mtgscanner.data.db.Box
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.Loan
import kotlinx.coroutines.flow.first

/** Lending a whole deck: every copy kept in the deck's own card rows. Pure parts, for tests. */
object DeckLoanMath {
    /** The rows kept in [deckId] (its hidden card boxes). */
    fun rowsOf(deckId: Long, cards: List<CollectionCard>, places: List<Box>): List<CollectionCard> {
        val slots = places.filter { it.deckId == deckId && deckId != 0L }.map { it.id }.toSet()
        return cards.filter { it.boxId in slots }
    }

    /** Loans for the copies of [rows] still in hand, all to [who]. */
    fun loansFor(rows: List<CollectionCard>, loans: List<Loan>, all: List<CollectionCard>, who: String, at: Long): List<Loan> {
        val out = LoanMath.outByRow(loans, all)
        return rows.mapNotNull { c ->
            val left = c.quantity - (out[c.id] ?: 0)
            if (left <= 0) null else Loan(cardId = c.id, scryfallId = c.scryfallId, name = c.name, foil = c.foil, quantity = left, who = who, at = at)
        }
    }

    /**
     * Who has the deck, by deck id: "Sam" when its copies are lent to one person, "Sam, Ana" for several.
     * Only decks with every copy out are listed, so a card or two lent from a deck doesn't mark the whole deck.
     */
    fun lentTo(cards: List<CollectionCard>, places: List<Box>, loans: List<Loan>): Map<Long, String> {
        if (loans.isEmpty()) return emptyMap()
        val deckOfBox = places.filter { it.deckId != 0L }.associate { it.id to it.deckId }
        val out = LoanMath.outByRow(loans, cards)
        val byRow = HashMap<Long, MutableList<Loan>>()
        loans.forEach { l -> LoanMath.rowFor(l, cards)?.let { byRow.getOrPut(it.id) { mutableListOf() } += l } }
        return cards.filter { it.boxId in deckOfBox }.groupBy { deckOfBox.getValue(it.boxId) }.mapNotNull { (deck, rows) ->
            val allOut = rows.isNotEmpty() && rows.all { (out[it.id] ?: 0) >= it.quantity }
            if (!allOut) return@mapNotNull null
            val names = rows.flatMap { byRow[it.id].orEmpty() }.map { it.who }.distinctBy { it.lowercase() }
            deck to names.joinToString(", ")
        }.toMap()
    }
}

/** Lends every copy in the deck's card rows to [who]. Returns the loans made, for Undo; empty when none could be. */
suspend fun CollectionRepository.lendDeck(deckId: Long, who: String): List<Loan> {
    val name = LoanMath.cleanName(who)
    if (name.isEmpty()) return emptyList()
    val all = observeAll().first()
    val rows = DeckLoanMath.rowsOf(deckId, all, dao.allPlaces())
    val made = DeckLoanMath.loansFor(rows, dao.allLoans(), all, name, System.currentTimeMillis())
    if (made.isEmpty()) return emptyList()
    return db.withTransaction { made.map { l -> l.copy(id = dao.addLoan(l)) } }
}

/** The deck came back: takes off every loan on its card rows. Returns them, for Undo. */
suspend fun CollectionRepository.returnDeck(deckId: Long): List<Loan> {
    val all = observeAll().first()
    val rows = DeckLoanMath.rowsOf(deckId, all, dao.allPlaces()).map { it.id }.toSet()
    val loans = dao.allLoans().filter { l -> LoanMath.rowFor(l, all)?.let { it.id in rows } == true }
    if (loans.isEmpty()) return emptyList()
    db.withTransaction { loans.forEach { dao.deleteLoan(it.id) } }
    return loans
}
