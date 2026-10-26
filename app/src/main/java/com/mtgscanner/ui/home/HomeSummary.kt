package com.mtgscanner.ui.home

import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.artCropUrl
import com.mtgscanner.data.db.changeSinceAdded
import com.mtgscanner.data.db.priceWhenAdded
import com.mtgscanner.data.db.totalEur
import com.mtgscanner.data.db.totalPrice
import com.mtgscanner.data.db.unitPrice
import com.mtgscanner.ui.theme.AccentMath
import java.util.PriorityQueue

/**
 * Home's numbers in one pass over the collection, instead of a pass (and a copied list) per number.
 * Big collections redraw Home on every scan, so this keeps that cheap. Pure, for tests.
 */
object HomeSummary {
    fun of(all: List<CollectionCard>): HomeUi {
        var copies = 0
        var value = 0.0
        var eur = 0.0
        var foils = 0
        var atAdd = 0.0
        var nowTracked = 0.0
        val unique = HashSet<String>()
        val sets = HashSet<String>()
        val identity = ArrayList<Pair<String, Int>>(all.size)
        val byColor = IntArray(ColorSplit.KEYS.size)
        var top: CollectionCard? = null
        var topPrice = 0.0
        // Smallest first, so the head is the one to drop when a bigger one comes along.
        val recent = PriorityQueue<CollectionCard>(13, compareBy { it.addedAt })
        val gains = PriorityQueue<Pair<CollectionCard, Double>>(13, compareBy { it.second })
        for (c in all) {
            copies += c.quantity
            value += c.totalPrice()
            eur += c.totalEur()
            if (c.foil) foils += c.quantity
            unique += c.oracleId ?: c.name
            sets += c.setCode
            identity += c.colorIdentity to c.quantity
            byColor[ColorSplit.indexOf(c.colors)] += c.quantity
            keep(recent, c, 12)
            if (c.proxy) continue
            val unit = c.unitPrice() ?: 0.0
            if (unit > topPrice) { top = c; topPrice = unit }
            if (c.priceAtAdd != null) {
                atAdd += (c.priceWhenAdded() ?: 0.0) * c.quantity
                nowTracked += c.totalPrice()
            }
            val ch = c.changeSinceAdded() ?: 0.0
            if (ch > 0.0) keep(gains, c to ch * c.quantity, 12)
        }
        return HomeUi(
            loaded = true,
            totalCards = copies,
            uniqueCards = unique.size,
            totalValue = value,
            totalEur = eur,
            sets = sets.size,
            foils = foils,
            recent = drain(recent).sortedByDescending { it.addedAt },
            valueAtAdd = atAdd,
            valueNowTracked = nowTracked,
            gainers = drain(gains).sortedByDescending { it.second }.map { it.first },
            heroArt = artCropUrl(top?.imageUrl),
            colorLetter = AccentMath.dominant(identity),
            colorSplit = ColorSplit.KEYS.indices.map { ColorSplit.KEYS[it] to byColor[it] }.filter { it.second > 0 },
        )
    }

    private fun <T> keep(q: PriorityQueue<T>, item: T, n: Int) {
        q.add(item)
        if (q.size > n) q.poll()
    }

    private fun <T> drain(q: PriorityQueue<T>): List<T> = ArrayList(q)
}

/** The collection split by card color for the bar on Home: one color, several (M), or none (C). */
object ColorSplit {
    val KEYS = listOf("W", "U", "B", "R", "G", "M", "C")

    fun indexOf(colors: String): Int {
        val letters = colors.uppercase().filter { it in "WUBRG" }.toSet()
        return when (letters.size) {
            0 -> 6
            1 -> "WUBRG".indexOf(letters.first())
            else -> 5
        }
    }

    fun label(key: String): String = when (key) {
        "W" -> "White"; "U" -> "Blue"; "B" -> "Black"; "R" -> "Red"; "G" -> "Green"; "M" -> "Multicolor"; else -> "Colorless"
    }
}
