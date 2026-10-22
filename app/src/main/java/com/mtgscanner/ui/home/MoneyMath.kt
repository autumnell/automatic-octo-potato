package com.mtgscanner.ui.home

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** One month: what was paid for cards added, what sales brought in, and what the collection was worth at its end. */
data class MonthMoney(val month: YearMonth, val spent: Double, val earned: Double, val worth: Double?)

/** Money in and out by month. Pure Kotlin, so it can be tested without a phone. */
object MoneyMath {
    /**
     * The last [count] months up to [now], oldest first. [added] is (when added, amount paid) per card row,
     * [sales] is (when sold, amount) per sale, and [snapshots] is (yyyy-MM-dd, value). A month's worth is the
     * last value recorded on or before its end; null when there's none yet.
     */
    fun months(
        added: List<Pair<Long, Double>>,
        sales: List<Pair<Long, Double>>,
        snapshots: List<Pair<String, Double>>,
        now: YearMonth,
        zone: ZoneId = ZoneId.systemDefault(),
        count: Int = 6,
    ): List<MonthMoney> {
        fun monthOf(millis: Long) = YearMonth.from(Instant.ofEpochMilli(millis).atZone(zone))
        val spent = added.groupBy({ monthOf(it.first) }, { it.second })
        val earned = sales.groupBy({ monthOf(it.first) }, { it.second })
        val points = snapshots.mapNotNull { (day, v) -> runCatching { LocalDate.parse(day) }.getOrNull()?.let { it to v } }.sortedBy { it.first }
        return (count - 1 downTo 0).map { back ->
            val m = now.minusMonths(back.toLong())
            val end = m.atEndOfMonth()
            MonthMoney(
                month = m,
                spent = spent[m].orEmpty().sum(),
                earned = earned[m].orEmpty().sum(),
                worth = points.lastOrNull { !it.first.isAfter(end) }?.second,
            )
        }
    }

    /** Whether there's anything to show: some money spent or earned, or a value recorded. */
    fun hasData(months: List<MonthMoney>): Boolean = months.any { it.spent > 0 || it.earned > 0 || it.worth != null }
}
