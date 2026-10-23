package com.mtgscanner.ui.home

import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.totalEur
import com.mtgscanner.data.db.totalPrice
import java.util.Locale

/** A number Home can show in a stat tile. [label] is the picker's name, [short] the tile's. */
enum class TileMetric(val key: String, val label: String, val short: String, val money: Boolean = false) {
    TOTAL("total", "Total cards", "Cards"),
    UNIQUE("unique", "Unique", "Unique"),
    SETS("sets", "Sets", "Sets"),
    FOILS("foils", "Foils", "Foils"),
    VALUE("value", "Collection value", "Value", money = true),
    BOXES("boxes", "Boxes", "Boxes"),
    RARES("rares", "Rares", "Rares"),
    MYTHICS("mythics", "Mythics", "Mythics"),
    LANDS("lands", "Lands", "Lands"),
    COMMANDERS("commanders", "Commanders", "Commanders"),
    AVERAGE("average", "Average card value", "Avg card", money = true),
    BULK("bulk", "Bulk cards", "Bulk"),
    WEEK("week", "Added this week", "This week"),
    WISHLIST("wishlist", "Wishlist", "Wishlist"),
    FOR_SALE("sale", "For sale", "For sale"),
    PROXIES("proxies", "Proxies", "Proxies"),
    SIGNED("signed", "Signed", "Signed"),
    GRADED("graded", "Graded", "Graded"),
    /** Cards matching a saved search; the tile shows the search's name. */
    CUSTOM("custom", "Saved list…", "List"),
    ;

    companion object {
        val DEFAULT = listOf(UNIQUE, SETS, FOILS)
        const val MIN = 2
        const val MAX = 4
        fun of(key: String): TileMetric? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Browse filters behind stat tiles that have no filter of their own (see [com.mtgscanner.ui.collection.Filters.flags]).
 * Each matches the same copies its tile counts in [TileMath.counts].
 */
object TileFlags {
    const val COMMANDER = "commander"
    const val BULK = "bulk"
    const val FOR_SALE = "sale"
    const val PROXY = "proxy"
    const val SIGNED = "signed"
    const val GRADED = "graded"

    fun matches(c: CollectionCard, flag: String): Boolean = when (flag) {
        COMMANDER -> TileMath.isCommander(c)
        BULK -> HomeMath.bulk(listOf(c), 0.0).cards > 0
        FOR_SALE -> c.forSale
        PROXY -> c.proxy
        SIGNED -> c.signed
        GRADED -> c.graded.isNotBlank()
        else -> true
    }

    /** The flag behind [metric]'s tile, or null when the tile opens something else. */
    fun of(metric: TileMetric): String? = when (metric) {
        TileMetric.COMMANDERS -> COMMANDER
        TileMetric.BULK -> BULK
        TileMetric.FOR_SALE -> FOR_SALE
        TileMetric.PROXIES -> PROXY
        TileMetric.SIGNED -> SIGNED
        TileMetric.GRADED -> GRADED
        else -> null
    }
}

/** Tile numbers, kept free of Android so they can be tested. */
object TileMath {
    const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

    /** Could lead a Commander deck: a legendary creature, or a card that says it can. */
    fun isCommander(c: CollectionCard): Boolean =
        (c.typeLine.contains("Legendary", ignoreCase = true) && c.typeLine.contains("Creature", ignoreCase = true)) ||
            c.oracleText.contains("can be your commander", ignoreCase = true)

    /**
     * Every metric except [TileMetric.CUSTOM], counted in copies. Money values leave out proxies;
     * the average is value per non-proxy copy.
     */
    fun counts(cards: List<CollectionCard>, boxes: Int, wishlist: Int, now: Long): Map<TileMetric, Double> {
        fun copies(pred: (CollectionCard) -> Boolean) = cards.filter(pred).sumOf { it.quantity }.toDouble()
        val value = cards.sumOf { it.totalPrice() }
        val real = copies { !it.proxy }
        return mapOf(
            TileMetric.TOTAL to copies { true },
            TileMetric.UNIQUE to cards.map { it.oracleId ?: it.name }.distinct().size.toDouble(),
            TileMetric.SETS to cards.map { it.setCode }.distinct().size.toDouble(),
            TileMetric.FOILS to copies { it.foil },
            TileMetric.VALUE to value,
            TileMetric.BOXES to boxes.toDouble(),
            TileMetric.RARES to copies { it.rarity == "rare" },
            TileMetric.MYTHICS to copies { it.rarity == "mythic" },
            TileMetric.LANDS to copies { it.typeLine.contains("Land", ignoreCase = true) },
            TileMetric.COMMANDERS to copies(::isCommander),
            TileMetric.AVERAGE to if (real > 0) value / real else 0.0,
            TileMetric.BULK to HomeMath.bulk(cards, 0.0).cards.toDouble(),
            TileMetric.WEEK to copies { (now - it.addedAt) in 0L until WEEK_MS },
            TileMetric.WISHLIST to wishlist.toDouble(),
            TileMetric.FOR_SALE to copies { it.forSale },
            TileMetric.PROXIES to copies { it.proxy },
            TileMetric.SIGNED to copies { it.signed },
            TileMetric.GRADED to copies { it.graded.isNotBlank() },
        )
    }

    /** The money tiles ([TileMetric.VALUE], [TileMetric.AVERAGE]) in euros, counted as [counts] does. */
    fun eurCounts(cards: List<CollectionCard>): Map<TileMetric, Double> {
        val value = cards.sumOf { it.totalEur() }
        val real = cards.filter { !it.proxy }.sumOf { it.quantity }.toDouble()
        return mapOf(TileMetric.VALUE to value, TileMetric.AVERAGE to if (real > 0) value / real else 0.0)
    }

    /** Short enough for a quarter-width tile: "$1.2k", "€15", "$3.40", "1.2k". */
    fun text(metric: TileMetric, v: Double, euro: Boolean = false): String =
        if (metric.money) shortMoney(v, if (euro) "€" else "$") else shortCount(v)

    fun shortUsd(v: Double): String = shortMoney(v, "$")

    fun shortMoney(v: Double, sign: String): String = when {
        v >= 1_000_000 -> String.format(Locale.US, "%s%.1fM", sign, v / 1_000_000)
        v >= 10_000 -> String.format(Locale.US, "%s%.0fk", sign, v / 1000)
        v >= 1000 -> String.format(Locale.US, "%s%.1fk", sign, v / 1000)
        v >= 100 -> String.format(Locale.US, "%s%.0f", sign, v)
        else -> String.format(Locale.US, "%s%.2f", sign, v)
    }

    fun shortCount(v: Double): String {
        val n = v.toLong()
        return when {
            n >= 100_000 -> "${n / 1000}k"
            n >= 10_000 -> String.format(Locale.US, "%.1fk", n / 1000.0)
            else -> n.toString()
        }
    }
}
