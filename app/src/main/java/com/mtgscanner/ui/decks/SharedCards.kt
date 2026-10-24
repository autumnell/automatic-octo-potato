package com.mtgscanner.ui.decks

import com.mtgscanner.data.DeckCheck
import com.mtgscanner.util.CardNames
import com.mtgscanner.util.DeckLine

/** A card this deck plays that other decks' lists play too. */
data class SharedCard(val name: String, val decks: List<String>)

/** A card two decks both play: copies each needs, copies owned, and how many you're short to build both at once. */
data class ShortCard(val name: String, val needA: Int, val needB: Int, val owned: Int) {
    val short: Int get() = (needA + needB - owned).coerceAtLeast(0)
}

/** Cards shared between decks' lists, so you know what to pull before building two at once. Pure, for tests. */
object SharedCards {
    private val BASICS = setOf("plains", "island", "swamp", "mountain", "forest", "wastes")
        .flatMap { listOf(it, "snow-covered $it") }.toSet()

    private fun isBasic(name: String) = CardNames.front(name).trim().lowercase() in BASICS

    /**
     * This deck's played cards (basic lands left out) that are also played in [others] (deck name to its
     * lines), A to Z, each with those decks' names A to Z.
     */
    fun of(lines: List<DeckLine>, others: List<Pair<String, List<DeckLine>>>): List<SharedCard> {
        val otherKeys = others.map { (name, ls) ->
            name to ls.filter { it.section in DeckCheck.PLAYED }.map { CardNames.key(it.name) }.toSet()
        }
        return lines.filter { it.section in DeckCheck.PLAYED && !isBasic(it.name) }
            .distinctBy { CardNames.key(it.name) }
            .mapNotNull { l ->
                val k = CardNames.key(l.name)
                val decks = otherKeys.filter { k in it.second }.map { it.first }.sortedBy { it.lowercase() }
                if (decks.isEmpty()) null else SharedCard(CardNames.full(l.name), decks)
            }
            .sortedBy { it.name.lowercase() }
    }

    /**
     * Cards both [a] and [b] play (basic lands left out), with copies each needs and copies owned
     * ([owned] by [CardNames.key]). Cards you're short on come first, most short first, then A to Z.
     */
    fun both(a: List<DeckLine>, b: List<DeckLine>, owned: Map<String, Int>): List<ShortCard> {
        fun need(ls: List<DeckLine>) = ls.filter { it.section in DeckCheck.PLAYED && !isBasic(it.name) }
            .groupBy { CardNames.key(it.name) }.mapValues { (_, v) -> v.sumOf { it.quantity } to v.first().name }
        val na = need(a)
        val nb = need(b)
        return na.keys.intersect(nb.keys).map { k ->
            ShortCard(CardNames.full(na.getValue(k).second), na.getValue(k).first, nb.getValue(k).first, owned[k] ?: 0)
        }.sortedWith(compareByDescending<ShortCard> { it.short }.thenBy { it.name.lowercase() })
    }
}
