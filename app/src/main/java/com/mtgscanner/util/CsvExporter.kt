package com.mtgscanner.util

import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.unitPrice
import java.io.Writer
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** CSV layouts other collection apps import. */
enum class CsvFormat(val label: String, val fileTag: String) {
    FULL("Full", "collection"),
    MANABOX("ManaBox", "manabox"),
    MOXFIELD("Moxfield", "moxfield"),
    ARCHIDEKT("Archidekt", "archidekt"),
}

/**
 * Writes the collection as CSV. The full layout's first columns (Count, Name, Edition, Condition,
 * Language, Foil, Collector Number) follow the layout most collection sites accept for import.
 */
object CsvExporter {
    private val header = listOf(
        "Count", "Name", "Edition", "Condition", "Language", "Foil", "Collector Number",
        "Edition Name", "Rarity", "Type", "Mana Cost", "Mana Value", "Colors", "Price USD", "Tags", "Scryfall ID",
    )

    private val manaboxHeader = listOf(
        "Name", "Set code", "Set name", "Collector number", "Foil", "Rarity", "Quantity", "Scryfall ID", "Condition", "Language",
    )
    private val moxfieldHeader = listOf(
        "Count", "Tradelist Count", "Name", "Edition", "Condition", "Language", "Foil", "Tags", "Last Modified",
        "Collector Number", "Alter", "Proxy", "Purchase Price",
    )
    private val archidektHeader = listOf(
        "Quantity", "Name", "Finish", "Condition", "Date Added", "Language", "Category", "Tags", "Edition Name",
        "Edition Code", "Multiverse Id", "Scryfall ID", "Collector Number",
    )

    fun write(cards: List<CollectionCard>, out: Writer, format: CsvFormat = CsvFormat.FULL) {
        val head = when (format) {
            CsvFormat.FULL -> header
            CsvFormat.MANABOX -> manaboxHeader
            CsvFormat.MOXFIELD -> moxfieldHeader
            CsvFormat.ARCHIDEKT -> archidektHeader
        }
        out.write(head.joinToString(",") + "\n")
        for (c in cards.sortedWith(compareBy({ it.setName }, { it.name }))) {
            val row = when (format) {
                CsvFormat.FULL -> fullRow(c)
                CsvFormat.MANABOX -> manaboxRow(c)
                CsvFormat.MOXFIELD -> moxfieldRow(c)
                CsvFormat.ARCHIDEKT -> archidektRow(c)
            }
            out.write(row.joinToString(",") { escape(it) } + "\n")
        }
    }

    private fun price(c: CollectionCard) = c.unitPrice()?.let { "%.2f".format(Locale.US, it) } ?: ""

    /** What was paid per copy; blank when not recorded. */
    private fun paid(c: CollectionCard) = c.purchasePrice?.let { "%.2f".format(Locale.US, it) } ?: ""

    private fun fullRow(c: CollectionCard) = listOf(
        c.quantity.toString(), c.name, c.setCode.lowercase(), conditionName(c.condition), c.lang,
        if (c.foil) "foil" else "", c.collectorNumber, c.setName, c.rarity, c.typeLine, c.manaCost,
        c.cmc.toString(), c.colors, price(c), c.tags.replace(",", "; "), c.scryfallId,
    )

    /** ManaBox: lower-case set code, "foil"/"normal", Cardmarket-style condition keys, language codes. */
    private fun manaboxRow(c: CollectionCard) = listOf(
        c.name, c.setCode.lowercase(), c.setName, c.collectorNumber, if (c.foil) "foil" else "normal",
        c.rarity.lowercase(), c.quantity.toString(), c.scryfallId, manaboxCondition(c.condition), c.lang.ifBlank { "en" },
    )

    /** Moxfield: full condition and language names, "foil" or blank, True/False flags. */
    private fun moxfieldRow(c: CollectionCard) = listOf(
        c.quantity.toString(), "0", c.name, c.setCode.lowercase(), conditionName(c.condition), languageName(c.lang),
        if (c.foil) "foil" else "", userTags(c), moxfieldTime(c.updatedAt), c.collectorNumber,
        if (c.altered) "True" else "False", if (c.proxy) "True" else "False", paid(c),
    )

    /** Archidekt: Normal/Foil finish, short condition codes, upper-case language codes. */
    private fun archidektRow(c: CollectionCard) = listOf(
        c.quantity.toString(), c.name, if (c.foil) "Foil" else "Normal", archidektCondition(c.condition),
        day(c.addedAt), c.lang.ifBlank { "en" }.uppercase(), "", userTags(c), c.setName, c.setCode.lowercase(), "",
        c.scryfallId, c.collectorNumber,
    )

    private fun userTags(c: CollectionCard) =
        c.userTags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(",")

    internal fun conditionName(code: String) = when (code) {
        "NM" -> "Near Mint"; "LP" -> "Lightly Played"; "MP" -> "Moderately Played"
        "HP" -> "Heavily Played"; "DMG" -> "Damaged"; else -> code
    }

    internal fun manaboxCondition(code: String) = when (code) {
        "NM" -> "near_mint"; "LP" -> "excellent"; "MP" -> "good"
        "HP" -> "played"; "DMG" -> "poor"; else -> "near_mint"
    }

    internal fun archidektCondition(code: String) = when (code) {
        "NM", "LP", "MP", "HP" -> code; "DMG" -> "D"; else -> "NM"
    }

    internal fun languageName(code: String) = when (code.lowercase()) {
        "", "en" -> "English"; "ja" -> "Japanese"; "de" -> "German"; "fr" -> "French"; "it" -> "Italian"
        "es" -> "Spanish"; "pt" -> "Portuguese"; "ru" -> "Russian"; "ko" -> "Korean"
        "zhs" -> "Chinese Simplified"; "zht" -> "Chinese Traditional"; else -> code
    }

    private fun day(millis: Long) =
        if (millis <= 0) "" else DateTimeFormatter.ISO_LOCAL_DATE.format(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC))

    private val moxTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS", Locale.US)
    private fun moxfieldTime(millis: Long) =
        if (millis <= 0) "" else moxTime.format(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC))

    fun escape(v: String): String =
        if (v.any { it == ',' || it == '"' || it == '\n' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
}
