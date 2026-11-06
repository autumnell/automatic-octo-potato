package com.mtgscanner.ui.collection

import java.util.Locale
import kotlin.math.abs

/**
 * Price change over the last week, from price history. Pure Kotlin for tests.
 * History is (yyyy-MM-dd, price) pairs; "then" is the last price on or before [weekAgo],
 * as long as it isn't older than [oldest], so a gap in the history doesn't pass for a week.
 */
object WeekChange {
    const val DAYS = 7L
    /** How far before [weekAgo] a price may be and still count as last week's. */
    const val SLACK_DAYS = 7L

    /** One row: today's price for one copy, how many copies, and that printing's history. */
    data class Row(val now: Double?, val quantity: Int, val history: List<Pair<String, Double>>)

    fun then(history: List<Pair<String, Double>>, weekAgo: String, oldest: String): Double? =
        history.filter { it.first <= weekAgo && it.first >= oldest }.maxByOrNull { it.first }?.second

    /** One copy's change, or null when there's no price now or none from a week ago. */
    fun change(now: Double?, history: List<Pair<String, Double>>, weekAgo: String, oldest: String): Double? {
        if (now == null) return null
        val t = then(history, weekAgo, oldest) ?: return null
        return now - t
    }

    /** All rows' change in value; null when no row has a week of history. */
    fun total(rows: List<Row>, weekAgo: String, oldest: String): Double? {
        var any = false
        var sum = 0.0
        for (r in rows) {
            val c = change(r.now, r.history, weekAgo, oldest) ?: continue
            any = true
            sum += c * r.quantity
        }
        return if (any) sum else null
    }

    /** "+2.10", "−3.05" (a real minus) or "0.00"; "—" with no data. */
    /** [label] with the dollar sign, "+$2.10", for places where the number stands alone. */
    fun dollars(change: Double?): String {
        val l = label(change)
        return when {
            l == "—" -> l
            l[0] == '+' || l[0] == '−' -> "${l[0]}$" + l.substring(1)
            else -> "$" + l
        }
    }

    fun label(change: Double?): String {
        if (change == null) return "—"
        val v = if (abs(change) < 0.005) 0.0 else change
        val n = String.format(Locale.US, "%,.2f", abs(v))
        return when {
            v > 0 -> "+$n"
            v < 0 -> "−$n"
            else -> n
        }
    }

    /** A plain number for the price columns, "3,418.20"; the column header carries the currency. */
    fun amount(v: Double?): String = if (v == null) "—" else String.format(Locale.US, "%,.2f", v)
}
