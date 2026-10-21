package com.mtgscanner.widget

import kotlin.math.abs

/** A card whose price moved: [then] a week ago, [now] today. */
data class Mover(val name: String, val then: Double, val now: Double) {
    val change: Double get() = now - then
}

/** Pure text and math for the widgets, kept free of Android so it can be tested. */
object WidgetText {
    /** "Updated just now", "Updated 5m ago", "Updated 3h ago", "Updated 2d ago"; empty when never. */
    fun updated(at: Long, now: Long): String {
        if (at <= 0) return ""
        val min = ((now - at).coerceAtLeast(0)) / 60_000
        return when {
            min < 1 -> "Updated just now"
            min < 60 -> "Updated ${min}m ago"
            min < 24 * 60 -> "Updated ${min / 60}h ago"
            else -> "Updated ${min / (24 * 60)}d ago"
        }
    }

    /**
     * The [n] owned cards whose price moved most in dollars, at least [minChange]. [now] is (key, name, price today);
     * [then] is key to the price a week ago. A card with either price missing is left out.
     */
    fun movers(now: List<Triple<String, String, Double?>>, then: Map<String, Double>, n: Int = 3, minChange: Double = 0.25): List<Mover> =
        now.mapNotNull { (key, name, price) ->
            val old = then[key] ?: return@mapNotNull null
            price?.let { Mover(name, old, it) }
        }
            .distinctBy { it.name to it.then }
            .filter { abs(it.change) >= minChange }
            .sortedByDescending { abs(it.change) }
            .take(n)

    /** "+$1.20" / "−$0.80". */
    fun change(d: Double): String = (if (d >= 0) "+$" else "−$") + String.format(java.util.Locale.US, "%.2f", abs(d))
}
