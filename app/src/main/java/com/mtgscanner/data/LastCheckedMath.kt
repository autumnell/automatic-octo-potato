package com.mtgscanner.data

/** "Checked 3w ago" on box cards, and boxes ordered by their last Box check. Pure, for tests. */
object LastCheckedMath {
    const val DAY_MS = 24L * 60 * 60 * 1000

    /** "Checked today", "Checked 3d ago", "Checked 3w ago", "Checked 4mo ago", "Checked 2y ago"; null when never checked. */
    fun label(at: Long?, now: Long): String? {
        if (at == null || at <= 0) return null
        val days = ((now - at).coerceAtLeast(0) / DAY_MS).toInt()
        return "Checked " + when {
            days < 1 -> "today"
            days < 7 -> "${days}d ago"
            days < 60 -> "${days / 7}w ago"
            days < 365 -> "${days / 30}mo ago"
            else -> "${days / 365}y ago"
        }
    }

    /** Boxes never checked first, then the longest since a check. Ties keep their order. */
    fun <T> oldestFirst(items: List<T>, id: (T) -> Long, checks: Map<Long, Long>): List<T> =
        items.sortedBy { checks[id(it)] ?: Long.MIN_VALUE }
}
