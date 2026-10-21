package com.mtgscanner.ui.decks

import com.mtgscanner.util.DeckLine

/** What the deck stats need to know about one card. */
data class DeckCardInfo(
    val name: String,
    val cmc: Double,
    val manaCost: String,
    val typeLine: String,
    /** Color letters, e.g. "WU". Empty means colorless. */
    val colors: String,
    val colorIdentity: String,
    val oracleText: String,
    val imageUrl: String?,
)

/** Chart groups, in display order. */
enum class StatColor(val label: String, val letter: Char) {
    White("White", 'W'), Blue("Blue", 'U'), Black("Black", 'B'), Red("Red", 'R'), Green("Green", 'G'),
    Colorless("Colorless", 'C');

    companion object {
        val Five = listOf(White, Blue, Black, Red, Green)
        fun of(letter: Char): StatColor? = entries.firstOrNull { it.letter == letter.uppercaseChar() }
    }
}

data class DeckStats(
    /**
     * Mana value 0..7 (7 = 7+): count of nonland cards per color group. A multicolor card counts once for
     * each of its colors, so use [curveCounts] for the number of cards.
     */
    val curve: List<Map<StatColor, Int>>,
    /** Nonland cards per color group. A multicolor card counts once for each of its colors. */
    val byColor: Map<StatColor, Int>,
    /** Mana value 0..7 (7 = 7+): number of nonland cards. */
    val curveCounts: List<Int>,
    val averageMv: Double,
    val nonland: Int,
    val lands: Int,
    /** Colored pips in mana costs; Colorless holds {C} and generic mana. */
    val pips: Map<StatColor, Int>,
    /** Copies of mana sources that can make each color. */
    val production: Map<StatColor, Int>,
    /** Deck lines with no card data. */
    val unknown: Int,
)

object DeckStatsMath {
    /** Sections that count for stats. */
    private val PLAYED = setOf("Main", "Commander", "Companion")
    private val SYMBOL = Regex("""\{([^}]+)\}""")
    private val REMINDER = Regex("""\([^)]*\)""")
    private val BASICS = mapOf("Plains" to 'W', "Island" to 'U', "Swamp" to 'B', "Mountain" to 'R', "Forest" to 'G', "Wastes" to 'C')

    fun key(name: String): String =
        com.mtgscanner.scan.CardTextParser.normalize(name.substringBefore(" // "))

    fun isLand(typeLine: String): Boolean = typeLine.substringBefore(" // ").contains("Land")

    /** The color groups a card counts toward: each of its colors, or Colorless. */
    fun groups(colors: String): List<StatColor> {
        val up = colors.uppercase()
        return StatColor.Five.filter { it.letter in up }.ifEmpty { listOf(StatColor.Colorless) }
    }

    /** Pips in a mana cost like "{2}{W}{U/B}". Hybrid counts each color. Only the front face. */
    fun pips(cost: String): Map<StatColor, Int> {
        val out = mutableMapOf<StatColor, Int>()
        for (m in SYMBOL.findAll(cost.substringBefore(" // "))) {
            val s = m.groupValues[1].uppercase()
            val generic = s.toIntOrNull()
            if (generic != null) {
                if (generic > 0) out.merge(StatColor.Colorless, generic, Int::plus)
                continue
            }
            val letters = s.split("/").filter { it.length == 1 }.map { it[0] }
            var colored = false
            for (c in letters) if (c in "WUBRG") { out.merge(StatColor.of(c)!!, 1, Int::plus); colored = true }
            if (!colored && 'C' in letters) out.merge(StatColor.Colorless, 1, Int::plus)
        }
        return out
    }

    /** Colors a card can make, from basic land types and "Add …" text. W U B R G C letters. */
    fun produces(typeLine: String, oracle: String): Set<Char> {
        val out = mutableSetOf<Char>()
        BASICS.forEach { (type, c) -> if (Regex("\\b$type\\b").containsMatchIn(typeLine)) out += c }
        // Reminder text, like a Treasure's "(… Add one mana of any color.)", isn't the card making mana.
        for (sentence in oracle.replace(REMINDER, "").split('.', '\n')) {
            val i = sentence.indexOf("Add ", ignoreCase = true)
            if (i < 0) continue
            val part = sentence.substring(i)
            if (part.contains("any color", true) || part.contains("any one color", true) ||
                part.contains("any combination of colors", true)
            ) out += "WUBRG".toList()
            for (m in SYMBOL.findAll(part)) {
                m.groupValues[1].uppercase().split("/").forEach { s -> if (s.length == 1 && s[0] in "WUBRGC") out += s[0] }
            }
        }
        return out
    }

    fun compute(lines: List<DeckLine>, info: Map<String, DeckCardInfo>): DeckStats {
        val curve = List(8) { mutableMapOf<StatColor, Int>() }
        val curveCounts = IntArray(8)
        val byColor = mutableMapOf<StatColor, Int>()
        val pips = mutableMapOf<StatColor, Int>()
        val production = mutableMapOf<StatColor, Int>()
        var mvSum = 0.0
        var nonland = 0
        var lands = 0
        var unknown = 0
        for (l in lines) {
            if (l.section !in PLAYED) continue
            val c = info[key(l.name)]
            if (c == null) { unknown++; continue }
            val q = l.quantity
            val land = isLand(c.typeLine)
            if (land) lands += q else {
                nonland += q
                mvSum += c.cmc * q
                val mv = c.cmc.toInt().coerceIn(0, 7)
                curveCounts[mv] += q
                for (g in groups(c.colors)) {
                    curve[mv].merge(g, q, Int::plus)
                    byColor.merge(g, q, Int::plus)
                }
                pips(c.manaCost).forEach { (k, v) -> pips.merge(k, v * q, Int::plus) }
            }
            // Lands and other mana sources (rocks, dorks).
            for (p in produces(c.typeLine, c.oracleText)) {
                StatColor.of(p)?.let { production.merge(it, q, Int::plus) }
            }
        }
        return DeckStats(
            curve = curve, byColor = byColor, curveCounts = curveCounts.toList(),
            averageMv = if (nonland == 0) 0.0 else mvSum / nonland,
            nonland = nonland, lands = lands, pips = pips, production = production, unknown = unknown,
        )
    }
}
