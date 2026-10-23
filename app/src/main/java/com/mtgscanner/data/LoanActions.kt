package com.mtgscanner.data

import com.mtgscanner.data.db.Loan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Copies lent out, newest first. */
fun CollectionRepository.observeLoans(): Flow<List<Loan>> = dao.observeLoans()

/**
 * Marks [qty] copies of a row as lent to [who], never more than are still in hand.
 * Returns the loan, or null when the card or name is missing or every copy is already out.
 */
suspend fun CollectionRepository.lend(cardId: Long, qty: Int, who: String): Loan? {
    val name = LoanMath.cleanName(who)
    if (name.isEmpty()) return null
    val c = cardOnce(cardId) ?: return null
    val left = c.quantity - LoanMath.outCount(c, dao.observeLoans().first(), observeAll().first())
    if (left <= 0) return null
    val n = qty.coerceIn(1, left)
    val loan = Loan(cardId = c.id, scryfallId = c.scryfallId, name = c.name, foil = c.foil, quantity = n, who = name)
    val id = dao.addLoan(loan)
    logEvent(c.scryfallId, c.name, "lent", "×$n · $name")
    return loan.copy(id = id)
}

/** The copies came back. */
suspend fun CollectionRepository.returned(loan: Loan) {
    dao.deleteLoan(loan.id)
    logEvent(loan.scryfallId, loan.name, "returned", loan.who)
}

/** Undoes [returned] or [lend]'s removal: puts the loan back as it was. */
suspend fun CollectionRepository.putLoanBack(loan: Loan) {
    dao.putLoans(listOf(loan))
}

/** Takes a loan off without a "returned" note, to undo lending. */
suspend fun CollectionRepository.cancelLoan(loan: Loan) {
    dao.deleteLoan(loan.id)
}
