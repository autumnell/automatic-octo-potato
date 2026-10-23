package com.mtgscanner.data

import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.Loan
import com.mtgscanner.data.db.totalEur
import com.mtgscanner.data.db.unitEur
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoanMathTest {
    private fun card(id: Long, sid: String = "s$id", qty: Int = 1, foil: Boolean = false) = CollectionCard(
        id = id, scryfallId = sid, oracleId = sid, name = "Card $id", setCode = "m10", setName = "M10",
        collectorNumber = "1", rarity = "rare", typeLine = "Instant", manaCost = "", cmc = 1.0, colors = "R",
        colorIdentity = "R", oracleText = "", tags = "", priceUsd = 2.0, priceUsdFoil = 4.0, imageUrl = null,
        smallImageUrl = null, scryfallUri = "", lang = "en", releasedAt = null, artist = null, foil = foil, quantity = qty,
        boxId = 1, addedAt = 0, updatedAt = 0,
    )

    private fun loan(id: Long, cardId: Long, sid: String = "s$cardId", qty: Int = 1, who: String = "Kenji", at: Long = id, foil: Boolean = false) =
        Loan(id = id, cardId = cardId, scryfallId = sid, name = "Card $cardId", foil = foil, quantity = qty, who = who, at = at)

    @Test fun labelForAllAndSomeCopies() {
        val a = card(1, qty = 1)
        val b = card(2, qty = 3)
        val cards = listOf(a, b)
        assertEquals("Out · Kenji", LoanMath.label(a, listOf(loan(1, 1)), cards))
        assertEquals("2 out · Kenji, Ana", LoanMath.label(b, listOf(loan(1, 2), loan(2, 2, who = "Ana")), cards))
        assertNull(LoanMath.label(a, listOf(loan(1, 2)), cards))
    }

    @Test fun outNeverMoreThanTheRowHolds() {
        val a = card(1, qty = 2)
        assertEquals(2, LoanMath.outCount(a, listOf(loan(1, 1, qty = 5)), listOf(a)))
    }

    @Test fun mergedRowIsFoundBySameprintingAndFinish() {
        val kept = card(7, sid = "x", qty = 4)
        val foil = card(8, sid = "x", foil = true)
        val l = loan(1, cardId = 3, sid = "x")
        assertEquals(kept, LoanMath.rowFor(l, listOf(foil, kept)))
        assertEquals(mapOf(7L to 1), LoanMath.outByRow(listOf(l), listOf(foil, kept)))
        assertNull(LoanMath.rowFor(loan(2, cardId = 3, sid = "gone"), listOf(kept)))
    }

    @Test fun byPersonOldestFirstIgnoringCase() {
        val people = LoanMath.byPerson(listOf(loan(1, 1, who = "Ana", at = 50), loan(2, 2, who = "kenji", at = 10), loan(3, 3, who = "Kenji", at = 20)))
        assertEquals(listOf("kenji", "Ana"), people.map { it.first })
        assertEquals(listOf(2L, 3L), people[0].second.map { it.id })
    }

    @Test fun namesAreTidied() {
        assertEquals("Kenji M", LoanMath.cleanName("  Kenji   M "))
        assertEquals(listOf("Ana", "Kenji"), LoanMath.recentNames(listOf(loan(1, 1, who = "Kenji", at = 1), loan(2, 2, who = "Ana", at = 5), loan(3, 3, who = "kenji", at = 0))))
    }

    @Test fun euroPrices() {
        val c = card(1, qty = 2).copy(priceEur = 1.5, priceEurFoil = 3.0)
        assertEquals(1.5, c.unitEur()!!, 0.001)
        assertEquals(3.0, c.copy(foil = true).unitEur()!!, 0.001)
        // An old JustTCG condition price no longer counts.
        assertEquals(1.5, c.copy(conditionPrice = 1.0).unitEur()!!, 0.001)
        assertEquals(3.0, c.totalEur(), 0.001)
        assertEquals(0.0, c.copy(proxy = true).totalEur(), 0.001)
        assertNull(c.copy(priceEur = null).unitEur())
    }
}
