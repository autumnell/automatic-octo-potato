package com.mtgscanner.widget

/** Pure text for the widget, kept free of Android so it can be tested. */
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

    /** "Prices updated 3h ago" for Home; empty when prices were never fetched. */
    fun pricesUpdated(at: Long, now: Long): String =
        updated(at, now).takeIf { it.isNotEmpty() }?.let { "Prices " + it.replaceFirstChar { c -> c.lowercaseChar() } }.orEmpty()
}
