package com.mtgscanner.ui.trade

import com.mtgscanner.util.DeckLine

/** Card names compared loosely: case, extra spaces, and the back face of double-faced cards don't matter. */
internal fun wantKey(name: String): String =
    name.substringBefore(" // ").substringBefore("//").trim().lowercase().replace(Regex("\\s+"), " ")

/**
 * Which of [items] a friend's want list asks for, and how many of each to give.
 * Copies of the asked printing (set) go first; any printing counts.
 */
internal fun <T> matchWants(
    wants: List<DeckLine>,
    items: List<T>,
    name: (T) -> String,
    set: (T) -> String,
    qty: (T) -> Int,
): List<Pair<T, Int>> {
    val byName = items.groupBy { wantKey(name(it)) }
    val wanted = wants.groupBy { wantKey(it.name) }
    val out = mutableListOf<Pair<T, Int>>()
    for ((key, lines) in wanted) {
        val rows = byName[key] ?: continue
        val sets = lines.mapNotNull { it.setCode?.lowercase() }.toSet()
        var left = lines.sumOf { it.quantity }
        for (row in rows.sortedByDescending { set(it).lowercase() in sets }) {
            if (left <= 0) break
            val n = minOf(left, qty(row))
            if (n > 0) { out += row to n; left -= n }
        }
    }
    return out
}
