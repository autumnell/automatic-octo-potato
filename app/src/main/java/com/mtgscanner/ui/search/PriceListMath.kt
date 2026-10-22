package com.mtgscanner.ui.search

/**
 * One line of a pasted list after lookup: how many, the price per copy (null when Scryfall has none),
 * how many you already own, and whether the card was found at all.
 */
data class PricedLine(
    val name: String,
    val quantity: Int,
    val unitPrice: Double?,
    val owned: Int = 0,
    val found: Boolean = true,
) {
    val lineTotal: Double? get() = unitPrice?.let { it * quantity }

    /** Copies you'd still need. */
    val short: Int get() = if (found) (quantity - owned).coerceAtLeast(0) else 0
}

/** Totals for a priced list. */
data class PriceTotals(
    /** Copies on the list, found or not. */
    val cards: Int,
    /** Sum of every priced line. */
    val total: Double,
    /** Lines found but with no price. */
    val unpriced: Int,
    /** Lines Scryfall couldn't find. */
    val notFound: Int,
    /** Copies you'd still need, and what they cost. */
    val shortCards: Int,
    val shortValue: Double,
)

/** Totals for "Price a list"; pure so it can be tested. */
object PriceListMath {
    fun totals(lines: List<PricedLine>): PriceTotals = PriceTotals(
        cards = lines.sumOf { it.quantity },
        total = lines.sumOf { it.lineTotal ?: 0.0 },
        unpriced = lines.count { it.found && it.unitPrice == null },
        notFound = lines.count { !it.found },
        shortCards = lines.sumOf { it.short },
        shortValue = lines.sumOf { l -> l.unitPrice?.let { it * l.short } ?: 0.0 },
    )

    /** Lines with copies you don't own yet, in list order. */
    fun short(lines: List<PricedLine>): List<PricedLine> = lines.filter { it.short > 0 }
}
