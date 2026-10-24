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
        )
    }

    private fun <T> keep(q: PriorityQueue<T>, item: T, n: Int) {
        q.add(item)
        if (q.size > n) q.poll()
    }

    private fun <T> drain(q: PriorityQueue<T>): List<T> = ArrayList(q)
}
