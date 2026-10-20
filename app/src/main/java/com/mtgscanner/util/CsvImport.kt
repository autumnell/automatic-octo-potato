package com.mtgscanner.util

/** One card row from another app's export. */
data class ImportRow(
    val quantity: Int,
    val name: String,
    val setCode: String? = null,
    val collectorNumber: String? = null,
    val foil: Boolean = false,
    val scryfallId: String? = null,
    val condition: String? = null,
    val language: String? = null,
)

/**
 * Reads collection CSVs from ManaBox, Moxfield, Archidekt, Deckbox, DragonShield and this app,
 * by recognizing their column names.
 */
object CsvImport {
    fun parse(text: String): List<ImportRow> {
        val rows = readCsv(text.removePrefix("﻿"))
        if (rows.isEmpty()) return emptyList()
        // Some exports (DragonShield) start with a "sep=," line.
        val headerIndex = rows.indexOfFirst { r -> r.any { col(it) in NAME } }
        if (headerIndex < 0) return emptyList()
        val header = rows[headerIndex].map(::col)
        // Aliases are checked in order, so "Edition Code" wins over "Edition" (a full set name).
        fun idx(names: Set<String>) = names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } } ?: -1
        val iName = idx(NAME)
        val iQty = idx(QTY)
        val iSet = idx(SET_CODE)
        val iNum = idx(NUMBER)
        val iFoil = idx(FOIL)
        val iId = idx(SCRYFALL_ID)
        val iCond = idx(CONDITION)
        val iLang = idx(LANGUAGE)

        return rows.drop(headerIndex + 1).mapNotNull { r ->
            fun get(i: Int) = if (i >= 0) r.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() } else null
            val name = get(iName) ?: return@mapNotNull null
            val qty = get(iQty)?.toDoubleOrNull()?.toInt() ?: 1
            if (qty <= 0) return@mapNotNull null
            val foilText = get(iFoil)?.lowercase().orEmpty()
            ImportRow(
                quantity = qty,
                name = name,
                // A set code is short with no spaces; anything else is a set name and is ignored.
                setCode = get(iSet)?.lowercase()?.takeIf { it.length <= 6 && ' ' !in it },
                collectorNumber = get(iNum),
                foil = foilText in setOf("foil", "true", "yes", "1", "etched", "y"),
                scryfallId = get(iId)?.takeIf { it.length == 36 },
                condition = get(iCond)?.let(::conditionCode),
                language = get(iLang),
            )
        }
    }

    private fun col(s: String) = s.trim().lowercase().replace("_", " ")

    private val NAME = setOf("name", "card name", "card")
    private val QTY = setOf("quantity", "count", "qty", "amount")
    private val SET_CODE = setOf("set code", "edition code", "setcode", "set", "edition")
    private val NUMBER = setOf("collector number", "card number", "number", "collector #", "cn")
    private val FOIL = setOf("foil", "finish", "printing", "foil/variant")
    private val SCRYFALL_ID = setOf("scryfall id", "scryfallid", "scryfall uuid")
    private val CONDITION = setOf("condition")
    private val LANGUAGE = setOf("language", "lang")

    fun conditionCode(s: String): String = when (s.trim().lowercase().replace("_", " ")) {
        "mint", "near mint", "nm", "m", "nm-m", "nearmint" -> "NM"
        "lightly played", "lp", "excellent", "ex", "slightly played", "sp", "good (lightly played)", "lightlyplayed" -> "LP"
        "moderately played", "mp", "good", "gd", "played", "pl", "moderatelyplayed" -> "MP"
        "heavily played", "hp", "poor", "heavilyplayed" -> "HP"
        "damaged", "dmg", "dm" -> "DMG"
        else -> "NM"
    }

    /** Minimal RFC 4180 reader: quotes, escaped quotes and newlines inside quotes. */
    fun readCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ } else inQuotes = false
                } else field.append(c)
            } else when (c) {
                '"' -> inQuotes = true
                ',' -> { row += field.toString(); field.clear() }
                '\r' -> {}
                '\n' -> { row += field.toString(); field.clear(); rows += row; row = mutableListOf() }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row += field.toString(); rows += row }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }
}
