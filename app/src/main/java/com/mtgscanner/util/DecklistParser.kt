package com.mtgscanner.util

/** One line of a decklist: how many, which card, and optionally the printing. */
data class DeckLine(
    val quantity: Int,
    val name: String,
    val setCode: String? = null,
    val collectorNumber: String? = null,
    val section: String = "Main",
)

/**
 * Reads pasted decklists in the common formats: "4 Lightning Bolt", "4x Lightning Bolt",
 * "1 Sol Ring (CMR) 263", "SB: 2 Duress", plus section headers like "Sideboard" or "Commander".
 */
object DecklistParser {
    private val SECTION = Regex("""^(?://\s*)?(main\s*deck|maindeck|deck|mainboard|sideboard|side|commander|commanders|companion|maybeboard|considering|tokens?)\s*:?\s*(\(\d+\))?$""", RegexOption.IGNORE_CASE)
    private val LINE = Regex("""^(?:(SB|CMDR):\s*)?(\d+)\s*[xX]?\s+(.+?)\s*$""")
    private val PRINTING = Regex("""^(.+?)\s+[\(\[]([A-Za-z0-9]{2,6})[\)\]](?:\s+([A-Za-z0-9\-★]+))?(?:\s+\*[A-Z]\*)?$""")

    fun parse(text: String): List<DeckLine> {
        var section = "Main"
        val out = mutableListOf<DeckLine>()
        for (raw in text.lines()) {
            val line = raw.trim().trimStart('-', '•', '*').trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val header = SECTION.matchEntire(line)
            if (header != null) {
                section = sectionName(header.groupValues[1])
                continue
            }
            if (line.startsWith("//")) continue

            val m = LINE.matchEntire(line)
            val qty: Int
            var rest: String
            var lineSection = section
            if (m != null) {
                qty = m.groupValues[2].toInt()
                rest = m.groupValues[3]
                when (m.groupValues[1].uppercase()) {
                    "SB" -> lineSection = "Sideboard"
                    "CMDR" -> lineSection = "Commander"
                }
            } else {
                qty = 1
                rest = line
            }
            // Moxfield/Arena tags like "*F*" (foil) at the end.
            rest = rest.replace(Regex("""\s+\*[A-Z]+\*$"""), "").trim()
            val p = PRINTING.matchEntire(rest)
            val entry = if (p != null) {
                DeckLine(qty, p.groupValues[1].trim(), p.groupValues[2].lowercase(), p.groupValues[3].ifEmpty { null }, lineSection)
            } else {
                DeckLine(qty, rest, section = lineSection)
            }
            if (entry.name.isNotBlank() && entry.quantity > 0) out += entry
        }
        // Merge duplicates in the same section.
        return out.groupBy { Triple(it.name.lowercase(), it.section, it.setCode) }
            .map { (_, v) -> v.first().copy(quantity = v.sumOf { it.quantity }) }
    }

    private fun sectionName(s: String): String = when (s.lowercase().replace(" ", "")) {
        "sideboard", "side" -> "Sideboard"
        "commander", "commanders" -> "Commander"
        "companion" -> "Companion"
        "maybeboard", "considering" -> "Maybeboard"
        "token", "tokens" -> "Tokens"
        else -> "Main"
    }
}
