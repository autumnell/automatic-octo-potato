package com.mtgscanner.ui.home

import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.PricePoint
import com.mtgscanner.data.db.ValueSnapshot
import com.mtgscanner.data.db.nearMintPrice
import com.mtgscanner.data.db.unitPrice
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A card picked out for the year, with the amount it's picked for (price or gain). */
data class YearCard(
    val id: Long,
    val name: String,
    val setCode: String,
    val imageUrl: String?,
    val amount: Double,
)

data class YearReview(
    val year: Int,
    /** Copies added during the year. */
    val added: Int,
    /** The most valuable card added (price per copy). */
    val bestPull: YearCard?,
    /** The card whose copies gained the most value during the year. */
    val biggestGain: YearCard?,
    /** The set with the most copies added: code, name, copies. */
    val topSet: Triple<String, String, Int>?,
    /** Collection value at the first and last snapshot of the year. */
    val valueStart: Double?,
    val valueEnd: Double?,
) {
    val isEmpty: Boolean get() = added == 0 && valueStart == null && biggestGain == null
}

object YearReviewMath {
    /** Years worth showing, newest first: this year, plus any year cards were added or a value was recorded. */
    fun years(cards: List<CollectionCard>, snapshots: List<ValueSnapshot>, today: LocalDate, zone: ZoneId): List<Int> {
        val ys = sortedSetOf(today.year)
        cards.forEach { ys += yearOf(it.addedAt, zone) }
        snapshots.forEach { s -> s.day.take(4).toIntOrNull()?.let { ys += it } }
        return ys.filter { it in 1993..today.year }.sortedDescending()
    }

    fun yearOf(millis: Long, zone: ZoneId): Int = Instant.ofEpochMilli(millis).atZone(zone).year

    fun compute(
        year: Int,
        cards: List<CollectionCard>,
        snapshots: List<ValueSnapshot>,
        points: List<PricePoint>,
        today: LocalDate,
        zone: ZoneId,
    ): YearReview {
        val real = cards.filter { !it.proxy }
        val added = real.filter { yearOf(it.addedAt, zone) == year }

        val best = added.mapNotNull { c -> c.unitPrice()?.takeIf { it > 0 }?.let { c to it } }
            .maxByOrNull { it.second }
            ?.let { (c, p) -> YearCard(c.id, c.name, c.setCode, c.imageUrl ?: c.smallImageUrl, p) }

        val topSet = added.groupBy { it.setCode.lowercase() }
            .map { (code, rows) -> Triple(code, rows.first().setName, rows.sumOf { it.quantity }) }
            .maxWithOrNull(compareBy<Triple<String, String, Int>> { it.third }.thenByDescending { it.first })

        val first = "$year-01-01"
        val last = "$year-12-31"
        val inYear = snapshots.filter { it.day in first..last }.sortedBy { it.day }

        val byPrinting = points.filter { it.day <= last }.groupBy { it.scryfallId }
        val current = year >= today.year
        val gain = real.filter { yearOf(it.addedAt, zone) <= year }.mapNotNull { c ->
            val history = byPrinting[c.scryfallId].orEmpty().sortedBy { it.day }
            fun price(p: PricePoint) = if (c.foil) p.usdFoil ?: p.usd else p.usd
            val start = if (yearOf(c.addedAt, zone) == year) c.priceAtAdd
            else history.firstOrNull { it.day >= first && price(it) != null }?.let(::price)
            val end = if (current) c.nearMintPrice()
            else history.lastOrNull { it.day >= first && price(it) != null }?.let(::price)
            if (start == null || end == null) null else c to (end - start) * c.quantity
        }.filter { it.second > 0.005 }.maxByOrNull { it.second }
            ?.let { (c, g) -> YearCard(c.id, c.name, c.setCode, c.imageUrl ?: c.smallImageUrl, g) }

        return YearReview(
            year = year,
            added = added.sumOf { it.quantity },
            bestPull = best,
            biggestGain = gain,
            topSet = topSet,
            valueStart = inYear.firstOrNull()?.value,
            valueEnd = inYear.lastOrNull()?.value,
        )
    }
}
