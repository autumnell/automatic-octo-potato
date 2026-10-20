package com.mtgscanner.ui.home

import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.PricePoint
import com.mtgscanner.data.db.nearMintPrice
import com.mtgscanner.data.db.unitPrice

/** A card whose price moved: per-copy price a week ago and now. */
data class PriceMover(val card: CollectionCard, val then: Double, val now: Double) {
    /** Change in value for all your copies. */
    val change: Double get() = (now - then) * card.quantity
}

/** Each box's value per day. [values] has one number per entry in [days]. */
data class BoxValues(val days: List<String>, val values: Map<Long, List<Double>>)

/** Bulk commons and uncommons and what a buyer would pay for them. */
data class BulkEstimate(val cards: Int, val value: Double)

/** Number crunching for Home, kept free of Android so it can be tested. */
object HomeMath {
    const val BULK_MAX = 0.50

    private fun price(p: PricePoint, foil: Boolean): Double? = if (foil) p.usdFoil ?: p.usd else p.usd

    /**
     * Box values over the last [maxDays] days that have price history: today's contents of each box
     * priced at each day's price. A card's last known price carries forward; before its first known
     * price, the first one is used; cards with no history use today's price. Proxies count as $0.
     */
    fun boxValues(cards: List<CollectionCard>, points: List<PricePoint>, maxDays: Int = 60): BoxValues {
        val days = points.asSequence().map { it.day }.distinct().sorted().toList().takeLast(maxDays)
        if (days.isEmpty()) return BoxValues(emptyList(), emptyMap())
        val byCard = points.groupBy { it.scryfallId }.mapValues { (_, v) -> v.sortedBy { it.day } }
        val out = HashMap<Long, DoubleArray>()
        for (c in cards) {
            val arr = out.getOrPut(c.boxId) { DoubleArray(days.size) }
            if (c.proxy) continue
            val hist = byCard[c.scryfallId].orEmpty()
            val first = hist.firstNotNullOfOrNull { price(it, c.foil) }
            val fallback = first ?: c.nearMintPrice() ?: 0.0
            var j = 0
            var last: Double? = null
            for (i in days.indices) {
                val d = days[i]
                while (j < hist.size && hist[j].day <= d) {
                    val p = price(hist[j], c.foil)
                    if (p != null) last = p
                    j++
                }
                arr[i] += (last ?: fallback) * c.quantity
            }
        }
        return BoxValues(days, out.mapValues { it.value.toList() })
    }

    /**
     * Cards whose price moved most since [weekAgo] (yyyy-MM-dd), biggest change in total value first.
     * "Then" is the last price on or before [weekAgo], or the oldest price when history is shorter.
     */
    fun movers(cards: List<CollectionCard>, points: List<PricePoint>, weekAgo: String, limit: Int = 12): List<PriceMover> {
        val byCard = points.groupBy { it.scryfallId }.mapValues { (_, v) -> v.sortedBy { it.day } }
        // One line per printing and finish, with copies from every box added up.
        val merged = cards.filter { !it.proxy }.groupBy { it.scryfallId to it.foil }.values
            .map { same -> same.first().copy(quantity = same.sumOf { it.quantity }) }
        return merged.mapNotNull { c ->
            val hist = byCard[c.scryfallId].orEmpty().mapNotNull { p -> price(p, c.foil)?.let { p.day to it } }
            if (hist.size < 2) return@mapNotNull null
            val now = hist.last().second
            val then = hist.lastOrNull { it.first <= weekAgo }?.second ?: hist.first().second
            PriceMover(c, then, now).takeIf { kotlin.math.abs(it.change) >= 0.01 }
        }.sortedByDescending { kotlin.math.abs(it.change) }.take(limit)
    }

    /** Commons and uncommons under [BULK_MAX] (or unpriced), valued at [ratePer1000] dollars per 1000. */
    fun bulk(cards: List<CollectionCard>, ratePer1000: Double): BulkEstimate {
        val n = cards.filter { !it.proxy && (it.rarity == "common" || it.rarity == "uncommon") && (it.unitPrice() ?: 0.0) < BULK_MAX }
            .sumOf { it.quantity }
        return BulkEstimate(n, n / 1000.0 * ratePer1000)
    }
}
