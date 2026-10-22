package com.mtgscanner.data

import com.mtgscanner.util.DeckLine
import com.mtgscanner.util.ImportRow

/** Which cards of a deck list "Add to collection" adds. Pure, for tests. */
object DeckAdd {
    /** Sections that are added; the maybeboard and tokens aren't. */
    val SECTIONS = setOf("Main", "Commander", "Companion", "Signature", "Sideboard")

    /**
     * Import rows for [lines]: each line's count, printing and foil marker as listed. With [onlyMissing], copies
     * you already own (by name, any printing, from [owned] keyed by [key]) are taken off first, played
     * sections before the sideboard, the same way the deck check counts them.
     */
    fun rows(lines: List<DeckLine>, owned: Map<String, Int>, onlyMissing: Boolean, key: (String) -> String): List<ImportRow> {
        val wanted = lines.filter { it.section in SECTIONS }
        val counts = IntArray(wanted.size) { wanted[it].quantity }
        if (onlyMissing) {
            val left = owned.toMutableMap()
            val order = wanted.indices.sortedBy { if (wanted[it].section == "Sideboard") 1 else 0 }
            for (i in order) {
                val k = key(wanted[i].name)
                val have = minOf(left[k] ?: 0, counts[i])
                left[k] = (left[k] ?: 0) - have
                counts[i] -= have
            }
        }
        return wanted.indices.filter { counts[it] > 0 }.map { i ->
            val l = wanted[i]
            ImportRow(quantity = counts[i], name = l.name, setCode = l.setCode, collectorNumber = l.collectorNumber, foil = l.foil)
        }
    }

    /** "Added 58 · 2 not found". */
    fun resultLine(added: Int, notFound: Int): String = buildString {
        append(if (added == 1) "Added 1 card" else "Added $added")
        if (notFound > 0) append(" · $notFound not found")
    }
}
