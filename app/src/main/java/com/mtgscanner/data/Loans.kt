package com.mtgscanner.data

import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.Loan

/** Who has which lent copies. Pure Kotlin, so it can be tested without a phone. */
object LoanMath {
    /**
     * The row a loan's copies live in: the row they were lent from, or, when that row was merged away
     * (moved into a box that already had the card), a row of the same printing and finish.
     */
    fun rowFor(loan: Loan, cards: List<CollectionCard>): CollectionCard? =
        cards.firstOrNull { it.id == loan.cardId }
            ?: cards.firstOrNull { it.scryfallId == loan.scryfallId && it.foil == loan.foil }

    /** Loans whose copies live in [card]'s row. */
    fun loansOf(card: CollectionCard, loans: List<Loan>, cards: List<CollectionCard>): List<Loan> =
        loans.filter { l -> l.cardId == card.id || (rowFor(l, cards)?.id == card.id) }

    /** Copies of [card] that are out, never more than the row holds. */
    fun outCount(card: CollectionCard, loans: List<Loan>, cards: List<CollectionCard>): Int =
        loansOf(card, loans, cards).sumOf { it.quantity }.coerceIn(0, card.quantity)

    /** Copies out per row id, for whole lists (each loan is matched to its row once). Not capped at the row's copies. */
    fun outByRow(loans: List<Loan>, cards: List<CollectionCard>): Map<Long, Int> {
        if (loans.isEmpty()) return emptyMap()
        val out = HashMap<Long, Int>()
        loans.forEach { l -> rowFor(l, cards)?.let { out[it.id] = (out[it.id] ?: 0) + l.quantity } }
        return out
    }

    /** "Out · Kenji" when every copy is out, "2 out · Kenji, Ana" when some are; null when none. */
    fun label(card: CollectionCard, loans: List<Loan>, cards: List<CollectionCard>): String? {
        val mine = loansOf(card, loans, cards)
        if (mine.isEmpty()) return null
        val n = outCount(card, loans, cards)
        val names = mine.map { it.who }.distinctBy { it.lowercase() }.joinToString(", ")
        return if (n >= card.quantity) "Out · $names" else "$n out · $names"
    }

    /** Loans by person, the person with the oldest loan first; each person's loans oldest first. */
    fun byPerson(loans: List<Loan>): List<Pair<String, List<Loan>>> =
        loans.groupBy { it.who.trim().lowercase() }
            .values
            .map { group -> group.sortedBy { it.at } }
            .sortedBy { it.first().at }
            .map { it.first().who to it }

    /** A name as typed, tidied: trimmed, single spaces, at most 30 characters. */
    fun cleanName(who: String): String = who.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ").take(30)

    /** Names lent to before, most recent first, for quick picks. */
    fun recentNames(loans: List<Loan>, limit: Int = 6): List<String> =
        loans.sortedByDescending { it.at }.map { it.who }.distinctBy { it.lowercase() }.take(limit)
}
