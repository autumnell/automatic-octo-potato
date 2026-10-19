package com.mtgscanner.util

import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.unitPrice
import java.io.Writer

/**
 * Writes the collection as CSV. The first columns (Count, Name, Edition, Condition, Language,
 * Foil, Collector Number) follow the layout most collection sites accept for import.
 */
object CsvExporter {
    private val header = listOf(
        "Count", "Name", "Edition", "Condition", "Language", "Foil", "Collector Number",
        "Edition Name", "Rarity", "Type", "Mana Cost", "Mana Value", "Colors", "Price USD", "Tags", "Scryfall ID",
    )

    fun write(cards: List<CollectionCard>, out: Writer) {
        out.write(header.joinToString(",") + "\n")
        for (c in cards.sortedWith(compareBy({ it.setName }, { it.name }))) {
            val row = listOf(
                c.quantity.toString(), c.name, c.setCode.lowercase(), conditionName(c.condition), c.lang,
                if (c.foil) "foil" else "", c.collectorNumber, c.setName, c.rarity, c.typeLine, c.manaCost,
                c.cmc.toString(), c.colors, c.unitPrice()?.let { "%.2f".format(java.util.Locale.US, it) } ?: "",
                c.tags.replace(",", "; "), c.scryfallId,
            )
            out.write(row.joinToString(",") { escape(it) } + "\n")
        }
    }

    private fun conditionName(code: String) = when (code) {
        "NM" -> "Near Mint"; "LP" -> "Lightly Played"; "MP" -> "Moderately Played"
        "HP" -> "Heavily Played"; "DMG" -> "Damaged"; else -> code
    }

    fun escape(v: String): String =
        if (v.any { it == ',' || it == '"' || it == '\n' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
}
