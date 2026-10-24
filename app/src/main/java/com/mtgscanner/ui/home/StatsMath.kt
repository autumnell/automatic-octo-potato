package com.mtgscanner.ui.home

import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.totalEur
import com.mtgscanner.data.db.totalPrice
import com.mtgscanner.data.db.unitPrice
import java.time.Instant
import java.time.ZoneId

/** One line of a breakdown: copies and their value ([eur] in euros). [key] is what Browse filters on (set code, color letter, rarity). */
data class StatRow(val key: String, val label: String, val cards: Int, val value: Double, val eur: Double = 0.0)

/**
 * Copies with a price paid ([CollectionCard.purchasePrice]): what was paid for them and what they're worth
 * today. Proxies are left out on both sides.
 */
data class PaidSummary(val copies: Int = 0, val paid: Double = 0.0, val worth: Double = 0.0) {
    val change: Double get() = worth - paid
}

data class CollectionStats(
    val cards: Int = 0,
    val value: Double = 0.0,
    val valueEur: Double = 0.0,
    val bySet: List<StatRow> = emptyList(),
    /** W, U, B, R, G, then C. A multicolor card counts in full for each of its colors, so these add up to more than [value]. */
    val byColor: List<StatRow> = emptyList(),
    /** Common to mythic, then "special" for anything else. */
    val byRarity: List<StatRow> = emptyList(),
    /** Priciest single copies, proxies left out. */
    val top: List<CollectionCard> = emptyList(),
    val paid: PaidSummary = PaidSummary(),
    /** Commons and uncommons under 50¢, for the bulk estimate. */
    val bulkCards: Int = 0,
    /** By the year cards were added, newest first; [StatRow.key] is the year. */
    val byYear: List<StatRow> = emptyList(),
    /** By year: today's value minus what was paid (or the price when added), for copies where that's known. USD. */
    val yearChange: Map<String, Double> = emptyMap(),
)

/** Collection breakdowns for the Stats screen, kept free of Android so they can be tested. Proxies count as cards but are worth $0. */
object StatsMath {
    private val COLORS = listOf("W" to "White", "U" to "Blue", "B" to "Black", "R" to "Red", "G" to "Green", "C" to "Colorless")
    private val RARITY_ORDER = listOf("common", "uncommon", "rare", "mythic", "special", TOKENS)

    /** The rarity row key for tokens, emblems and art cards, kept apart so they don't swell the commons. */
    const val TOKENS = "tokens"

    fun compute(cards: List<CollectionCard>, topN: Int = 20, zone: ZoneId = ZoneId.systemDefault()): CollectionStats {
        val bySet = cards.groupBy { it.setCode }.map { (code, rows) ->
            StatRow(code, rows.first().setName.ifBlank { code.uppercase() }, rows.sumOf { it.quantity }, rows.sumOf { it.totalPrice() }, rows.sumOf { it.totalEur() })
        }.sortedWith(compareByDescending<StatRow> { it.value }.thenByDescending { it.cards }.thenBy { it.label })

        val colorCards = HashMap<String, Int>()
        val colorValue = HashMap<String, Double>()
        val colorEur = HashMap<String, Double>()
        cards.forEach { c ->
            val keys = if (c.colors.isEmpty()) listOf("C") else c.colors.map { it.toString() }.filter { k -> COLORS.any { it.first == k } }.distinct()
            keys.forEach { k ->
                colorCards[k] = (colorCards[k] ?: 0) + c.quantity
                colorValue[k] = (colorValue[k] ?: 0.0) + c.totalPrice()
                colorEur[k] = (colorEur[k] ?: 0.0) + c.totalEur()
            }
        }
        val byColor = COLORS.map { (k, name) -> StatRow(k, name, colorCards[k] ?: 0, colorValue[k] ?: 0.0, colorEur[k] ?: 0.0) }

        val byRarity = cards.groupBy {
            when {
                com.mtgscanner.data.CardExtras.isExtra(it.typeLine) -> TOKENS
                it.rarity in RARITY_ORDER -> it.rarity
                else -> "special"
            }
        }
            .map { (r, rows) ->
                StatRow(r, if (r == TOKENS) "Tokens & art" else r.replaceFirstChar(Char::uppercase), rows.sumOf { it.quantity }, rows.sumOf { it.totalPrice() }, rows.sumOf { it.totalEur() })
            }
            .sortedBy { RARITY_ORDER.indexOf(it.key) }

        val top = cards.filter { !it.proxy && (it.unitPrice() ?: 0.0) > 0.0 }
            .sortedWith(compareByDescending<CollectionCard> { it.unitPrice() ?: 0.0 }.thenBy { it.name })
            .take(topN)

        val paidRows = cards.filter { !it.proxy && it.purchasePrice != null }
        val paid = PaidSummary(
            copies = paidRows.sumOf { it.quantity },
            paid = paidRows.sumOf { (it.purchasePrice ?: 0.0) * it.quantity },
            worth = paidRows.sumOf { it.totalPrice() },
        )

        return CollectionStats(
            cards = cards.sumOf { it.quantity },
            value = cards.sumOf { it.totalPrice() },
            valueEur = cards.sumOf { it.totalEur() },
            bySet = bySet,
            byColor = byColor,
            byRarity = byRarity,
            top = top,
            paid = paid,
            bulkCards = HomeMath.bulk(cards, 0.0).cards,
            byYear = byYear(cards, zone),
            yearChange = yearChange(cards, zone),
        )
    }

    private fun yearOf(c: CollectionCard, zone: ZoneId) = Instant.ofEpochMilli(c.addedAt).atZone(zone).year.toString()

    /** Copies and value by the year they were added, newest first. */
    fun byYear(cards: List<CollectionCard>, zone: ZoneId = ZoneId.systemDefault()): List<StatRow> =
        cards.filter { it.addedAt > 0 }.groupBy { yearOf(it, zone) }
            .map { (y, rows) -> StatRow(y, y, rows.sumOf { it.quantity }, rows.sumOf { it.totalPrice() }, rows.sumOf { it.totalEur() }) }
            .sortedByDescending { it.key }

    /**
     * For each year: what copies added then are worth today minus what they cost (price paid, else the price
     * when added). Copies with neither, and proxies, are left out.
     */
    fun yearChange(cards: List<CollectionCard>, zone: ZoneId = ZoneId.systemDefault()): Map<String, Double> =
        cards.filter { it.addedAt > 0 && !it.proxy && (it.purchasePrice ?: it.priceAtAdd) != null }
            .groupBy { yearOf(it, zone) }
            .mapValues { (_, rows) -> rows.sumOf { it.totalPrice() - (it.purchasePrice ?: it.priceAtAdd ?: 0.0) * it.quantity } }
}
