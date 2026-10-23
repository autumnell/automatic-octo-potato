package com.mtgscanner.ui.components

import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.totalEur
import com.mtgscanner.data.db.totalPrice
import com.mtgscanner.data.db.unitEur
import com.mtgscanner.data.db.unitPrice
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.util.Money
import com.mtgscanner.util.PriceCurrency

/*
 * Card and collection values, in the currency picked in Settings › Prices (see [AppSettings.currency]).
 * Amounts that only exist in dollars (prices paid, asking prices, sales, buylists) keep using [formatUsd].
 */

fun formatEur(v: Double?): String = Money.eur(v)

/** A value given in both currencies, shown as Settings asks. */
fun formatMoney(usd: Double?, eur: Double?): String = Money.text(usd, eur, AppSettings.currency)

/** " · $12.00" after a count, or nothing when prices are hidden. Like [priceSuffix], in the chosen currency. */
fun moneySuffix(usd: Double?, eur: Double?): String = if (AppSettings.showPrices) " · ${formatMoney(usd, eur)}" else ""

/** For tight spots (tiles, narrow columns): one number, in euros only when Settings says EUR. */
fun formatMoneyShort(usd: Double?, eur: Double?): String =
    if (AppSettings.currency == PriceCurrency.EUR) formatEur(eur) else formatUsd(usd)

/** One copy's price. */
fun cardPrice(c: CollectionCard): String = formatMoney(c.unitPrice(), c.unitEur())

/** One copy's price as a single number, for tiles and narrow columns. */
fun cardPriceShort(c: CollectionCard): String = formatMoneyShort(c.unitPrice(), c.unitEur())

/** What all the row's copies are worth. */
fun cardTotal(c: CollectionCard): String = formatMoney(c.totalPrice(), c.totalEur())

/** A Scryfall printing's price in this finish. */
fun scryfallPrice(card: ScryfallCard, foil: Boolean): String {
    val eur = (if (foil) card.prices.eurFoil ?: card.prices.eur else card.prices.eur)?.toDoubleOrNull()
    return formatMoney(card.price(foil)?.toDoubleOrNull(), eur)
}

/** A Scryfall printing's price as a single number, for tiles. */
fun scryfallPriceShort(card: ScryfallCard, foil: Boolean): String {
    val eur = (if (foil) card.prices.eurFoil ?: card.prices.eur else card.prices.eur)?.toDoubleOrNull()
    return formatMoneyShort(card.price(foil)?.toDoubleOrNull(), eur)
}

/** Which number a single-number display (a chart, a rolling total) should show: euros only when Settings says EUR. */
fun primaryValue(usd: Double, eur: Double): Double = if (AppSettings.currency == PriceCurrency.EUR) eur else usd

/** Formats [primaryValue]'s number; with Both, the euro amount follows. */
fun formatPrimary(v: Double, eur: Double): String = when (AppSettings.currency) {
    PriceCurrency.USD -> formatUsd(v)
    PriceCurrency.EUR -> formatEur(v)
    PriceCurrency.BOTH -> "${formatUsd(v)} · ${formatEur(eur)}"
}
