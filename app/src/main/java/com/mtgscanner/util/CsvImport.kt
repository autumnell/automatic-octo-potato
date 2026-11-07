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
    /** Scryfall language code, like "en" or "ja". */
    val language: String? = null,
    /** What was paid per copy, when the export has it. */
    val purchasePrice: Double? = null,
    /** A full set name ("Magic 2010") when the file has no set code; turned into a code before lookup. */
    val setName: String? = null,
    val signed: Boolean = false,
    val altered: Boolean = false,
    val proxy: Boolean = false,
)

/**
 * Reads collection CSVs from ManaBox, Moxfield, Archidekt, Deckbox, Dragon Shield, TCGplayer, Delver Lens,
 * Cardmarket and this app, by recognizing their column names. Commas, semicolons or tabs.
 */
object CsvImport {
    private val COUNTED_LINE = Regex("""^\s*(?:SB:\s*|CMDR:\s*)?\d+\s*[xX]?\s+\S.*$""")

    /**
     * A collection CSV, or else a plain decklist ("4 Lightning Bolt", "1 Sol Ring (CMR) 263").
     * A decklist counts only when most lines start with a count, so random text isn't read as cards.
     * Maybeboard and token lines are left out.
     */
    fun parseAny(text: String): List<ImportRow> {
        parse(text).takeIf { it.isNotEmpty() }?.let { return it }
        val lines = text.removePrefix("\uFEFF").lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("//") }
        val counted = lines.count { COUNTED_LINE.matches(it) }
        if (counted == 0 || counted * 2 < lines.size) return emptyList()
        return DecklistParser.parse(text)
            .filter { it.section != "Maybeboard" && it.section != "Tokens" }
            .groupBy { listOf(it.name.lowercase(), it.setCode, it.collectorNumber, it.foil) }
            .map { (_, v) -> v.first().let { ImportRow(v.sumOf { l -> l.quantity }, it.name, it.setCode, it.collectorNumber, foil = it.foil) } }
    }

    fun parse(text: String): List<ImportRow> {
        var body = text.removePrefix("\uFEFF")
        // Excel writes "sep=;" as the first line to say which delimiter it used.
        val first = body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().removeSuffix("\r").trimStart()
        val delimiter = if (first.startsWith("sep=", ignoreCase = true) && first.length >= 5) {
            body = body.substringAfter(first).substringAfter('\n', "")
            first[4]
        } else guessDelimiter(first)
        val rows = readCsv(body, delimiter)
        if (rows.isEmpty()) return emptyList()
        val headerIndex = rows.indexOfFirst { r -> r.any { col(it) in NAME } }
        if (headerIndex < 0) return emptyList()
        val header = rows[headerIndex].map(::col)
        // Aliases are checked in order, so "Edition Code" wins over "Edition" (a full set name).
        fun idx(names: List<String>) = names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } } ?: -1
        val iName = idx(NAME)
        val iQty = idx(QTY)
        val iSet = idx(SET_CODE)
        val iSetName = idx(SET_NAME)
        val iNum = idx(NUMBER)
        val iFoil = idx(FOIL)
        val iId = idx(SCRYFALL_ID)
        val iCond = idx(CONDITION)
        val iLang = idx(LANGUAGE)
        val iPaid = idx(PURCHASE_PRICE)
        val iSigned = idx(SIGNED)
        val iAltered = idx(ALTERED)
        val iProxy = idx(PROXY)
        val cardmarketFile = header.any { it == "idproduct" || it == "english name" }
        val data = rows.drop(headerIndex + 1)
        // Cardmarket's grades (ManaBox, Dragon Shield, Cardmarket) put "Played" a step lower than TCGplayer's.
        val manaBox = "manabox id" in header || listOf("set code", "set name", "collector number", "scryfall id").all { it in header }
        val cardmarketScale = cardmarketFile || manaBox || "price bought" in header ||
            (iCond >= 0 && data.any { r -> r.getOrNull(iCond)?.let(::isCardmarketGrade) == true })

        return data.mapNotNull { r ->
            fun get(i: Int) = if (i >= 0) r.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() } else null
            fun flag(i: Int) = get(i)?.lowercase() in TRUE
            val name = get(iName) ?: return@mapNotNull null
            val qty = get(iQty)?.replace(',', '.')?.toDoubleOrNull()?.toInt() ?: 1
            if (qty <= 0) return@mapNotNull null
            val foilText = get(iFoil)?.lowercase().orEmpty()
            val condText = get(iCond)
            // TCGplayer writes "Near Mint Foil".
            val condFoil = condText?.trim()?.endsWith(" foil", ignoreCase = true) == true
            val setValue = get(iSet)
            // A set code is short with no spaces; anything else is a set name.
            val code = setValue?.lowercase()?.takeIf { it.length <= 6 && ' ' !in it && !cardmarketFile }
            ImportRow(
                quantity = qty,
                name = name,
                setCode = code,
                collectorNumber = get(iNum),
                foil = condFoil || foilText in FOIL_VALUES,
                scryfallId = get(iId)?.takeIf { it.length == 36 },
                condition = condText?.let { conditionCode(it.trim().removeSuffix(" Foil").removeSuffix(" foil"), cardmarketScale) },
                language = get(iLang)?.let { if (cardmarketFile) cardmarketLanguage(it) ?: languageCode(it) else languageCode(it) },
                purchasePrice = get(iPaid)?.let(::money),
                // A short all-letters word like "Theros" may be a set name, not a code: keep both and let the set list decide.
                setName = if (code == null) (get(iSetName) ?: setValue?.takeIf { !cardmarketFile })
                    else setValue?.takeIf { it.length >= 5 && it.all(Char::isLetter) },
                signed = flag(iSigned),
                altered = flag(iAltered),
                proxy = flag(iProxy),
            )
        }
    }

    /** The delimiter used most in the header line: comma, semicolon or tab. */
    internal fun guessDelimiter(header: String): Char {
        var inQuotes = false
        val counts = HashMap<Char, Int>()
        for (c in header) {
            if (c == '"') inQuotes = !inQuotes
            else if (!inQuotes && (c == ',' || c == ';' || c == '\t')) counts[c] = (counts[c] ?: 0) + 1
        }
        return listOf(',', ';', '\t').maxByOrNull { counts[it] ?: 0 }?.takeIf { (counts[it] ?: 0) > 0 } ?: ','
    }

    private fun col(s: String) = s.trim().lowercase().replace("_", " ")

    private val NAME = listOf("simple name", "name", "card name", "card", "english name")
    private val QTY = listOf("quantity", "count", "qty", "amount")
    private val SET_CODE = listOf("set code", "edition code", "setcode", "set", "edition", "exp.")
    private val SET_NAME = listOf("set name", "edition name", "exp. name", "expansion", "expansion name", "set", "edition")
    private val NUMBER = listOf("collector number", "card number", "number", "collector #", "cn", "collector's number", "collectors number")
    private val FOIL = listOf("foil", "finish", "printing", "foil/variant", "foil?")
    private val SCRYFALL_ID = listOf("scryfall id", "scryfallid", "scryfall uuid")
    private val CONDITION = listOf("condition")
    private val LANGUAGE = listOf("language", "lang")
    private val PURCHASE_PRICE = listOf("purchase price", "purchaseprice", "price paid", "acquired price", "price bought")
    private val SIGNED = listOf("signed", "signed?")
    private val ALTERED = listOf("altered", "alter", "altered art", "altered?")
    private val PROXY = listOf("proxy", "proxy?")
    private val TRUE = setOf("true", "yes", "1", "x", "y", "signed", "altered", "proxy")
    private val FOIL_VALUES = setOf("foil", "true", "yes", "1", "etched", "y", "x")
    private val CARDMARKET_GRADES = setOf("excellent", "ex", "good", "gd", "light played", "lightplayed", "pl", "po")

    private fun isCardmarketGrade(raw: String): Boolean =
        '_' in raw.trim() || col(raw) in CARDMARKET_GRADES

    /** Cardmarket numbers its languages. */
    internal fun cardmarketLanguage(s: String): String? = when (s.trim()) {
        "1" -> "en"; "2" -> "fr"; "3" -> "de"; "4" -> "es"; "5" -> "it"; "6" -> "zhs"
        "7" -> "ja"; "8" -> "pt"; "9" -> "ru"; "10" -> "ko"; "11" -> "zht"; else -> null
    }

    /** A price as [PriceInput] reads it ("$1.50", "1,50", "1.234,50"...); null when blank, zero or not a price. */
    internal fun money(s: String): Double? = PriceInput.parse(s)?.takeIf { it > 0 }

    /** Scryfall's language code for a language name or code, like "Japanese" or "JA" → "ja". */
    fun languageCode(s: String): String? = when (val l = s.trim().lowercase()) {
        "" -> null
        "english", "en" -> "en"
        "japanese", "jp", "ja" -> "ja"
        "german", "de" -> "de"
        "french", "fr" -> "fr"
        "italian", "it" -> "it"
        "spanish", "es", "sp" -> "es"
        "portuguese", "portuguese (brazil)", "pt" -> "pt"
        "russian", "ru" -> "ru"
        "korean", "ko", "kr" -> "ko"
        "chinese simplified", "simplified chinese", "zhs", "cs", "zh-hans", "zh_cn", "zh-cn" -> "zhs"
        "chinese traditional", "traditional chinese", "zht", "ct", "zh-hant", "zh_tw", "zh-tw" -> "zht"
        "phyrexian", "ph" -> "ph"
        else -> l.takeIf { it.length <= 3 && it.all(Char::isLetter) }
    }

    /**
     * The app's condition code. With [cardmarketScale] (ManaBox, Dragon Shield, Cardmarket), "Light Played" is
     * Moderately Played, "Played" is Heavily Played and "Poor" is Damaged, as this app's own ManaBox export writes them.
     */
    fun conditionCode(s: String, cardmarketScale: Boolean = false): String {
        val c = s.trim().lowercase().replace("_", " ")
        if (cardmarketScale) when (c) {
            "light played", "lightplayed", "lp" -> return "MP"
            "played", "pl" -> return "HP"
            "poor", "po" -> return "DMG"
        }
        return when (c) {
            "mint", "near mint", "nm", "m", "mt", "nm-m", "nearmint" -> "NM"
            "lightly played", "lp", "excellent", "ex", "slightly played", "sp", "good (lightly played)", "lightlyplayed", "light played", "lightplayed" -> "LP"
            "moderately played", "mp", "good", "gd", "played", "pl", "moderatelyplayed" -> "MP"
            "heavily played", "hp", "heavilyplayed" -> "HP"
            "damaged", "dmg", "dm", "d", "poor", "po" -> "DMG"
            else -> "NM"
        }
    }

    /** Minimal RFC 4180 reader: quotes, escaped quotes and newlines inside quotes. */
    fun readCsv(text: String, delimiter: Char = ','): List<List<String>> {
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
                delimiter -> { row += field.toString(); field.clear() }
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
