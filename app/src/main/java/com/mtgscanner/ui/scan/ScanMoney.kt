package com.mtgscanner.ui.scan

import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.ui.components.formatMoney
import com.mtgscanner.ui.components.moneySuffix

/** Dollar price of one copy in this finish. */
fun ScryfallCard.usdPrice(foil: Boolean): Double? = price(foil)?.toDoubleOrNull()

/** Euro price of one copy in this finish, picked the same way as the result card's. */
fun ScryfallCard.eurPrice(foil: Boolean): Double? = (if (foil) prices.eurFoil ?: prices.eur else prices.eur)?.toDoubleOrNull()

/** A scan list line's value, in the currency picked in Settings. */
fun entryMoney(e: ScanEntry): String = formatMoney(e.value, e.eurValue)

/** " · €12.00" for what adding [cards] would be worth, with the finish and copies they'll be added with. */
fun addWorth(state: ScanUiState, cards: List<ScryfallCard>, qty: Int): String {
    var usd = 0.0
    var eur = 0.0
    cards.forEach { c ->
        val foil = state.isFoil(c)
        usd += (c.usdPrice(foil) ?: 0.0) * qty
        eur += (c.eurPrice(foil) ?: 0.0) * qty
    }
    return moneySuffix(usd, eur)
}

/** Whether a photo or batch card is added as foil: the finish it was scanned with, else the scan bar's setting. */
fun ScanUiState.photoFoil(item: PhotoItem, card: ScryfallCard): Boolean =
    item.foil?.let { f -> f && card.hasFoil || !card.hasNonFoil } ?: isFoil(card)

/**
 * " · €12.00" for what adding the found [items] of a photo or batch would be worth: each with the finish and copies
 * it will be added with (a batch card keeps those it was scanned with; others take [qty]).
 */
fun photoWorth(state: ScanUiState, items: List<PhotoItem>, qty: Int): String {
    var usd = 0.0
    var eur = 0.0
    items.forEach { i ->
        val c = i.card ?: return@forEach
        val foil = state.photoFoil(i, c)
        val n = i.count ?: qty
        usd += (c.usdPrice(foil) ?: 0.0) * n
        eur += (c.eurPrice(foil) ?: 0.0) * n
    }
    return moneySuffix(usd, eur)
}
