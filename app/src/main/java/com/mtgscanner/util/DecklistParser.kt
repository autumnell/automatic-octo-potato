package com.mtgscanner.util

/** One line of a decklist: how many, which card, and optionally the printing. */
data class DeckLine(
    val quantity: Int,
    val name: String,
    val setCode: String? = null,
    val collectorNumber: String? = null,
    val section: String = "Main",
    /** Marked foil or etched ("*F*", "*E*"), as Moxfield and Archidekt write it. */
    val foil: Boolean = false,
)

/**
 * Reads pasted decklists in the common formats: "4 Lightning Bolt", "4x Lightning Bolt",
 * "1 Sol Ring (CMR) 263", "SB: 2 Duress", plus section headers like "Sideboard" or "Commander".
 * Also skips Arena's "About"/"Name" preamble, strips Archidekt tags, and reads an MTGO list's
 * last blank-line-separated block as the sideboard when the list has no headers.
 */
object DecklistParser {
    private val SECTION = Regex("""^(?://\s*)?(main\s*deck|maindeck|deck|mainboard|sideboard|side|commander|commanders|companion|maybeboard|considering|tokens?)\s*:?\s*(\(\d+\))?$""", RegexOption.IGNORE_CASE)
    private val LINE = Regex("""^(?:(SB|CMDR):\s*)?(\d+)\s*[xX]?\s+(.+?)\s*$""")
    private val PRINTING = Regex("""^(.+?)\s+[\(\[]([A-Za-z0-9]{2,6})[\)\]](?:\s+([A-Za-z0-9\-★†]+))?(?:\s+\*[A-Z]\*)?$""")
    // Deckstats puts the printing first: "[CMR#263] Sol Ring", "[CMR] Sol Ring".
    private val PREFIX_PRINTING = Regex("""^\[([A-Za-z0-9]{2,6})(?:#([A-Za-z0-9\-★†]+))?\]\s+(.+)$""")
    // MTGGoldfish variants: "Lightning Bolt <retro> [2X2]", "Forest <251> [THB]".
    private val ANGLE = Regex("""\s*<([^<>]*)>""")
    private val NUMBER = Regex("""[0-9]+[a-z★†]?""")
    private val FOIL_ANYWHERE = Regex("""\s\*(F|E|FOIL|ETCHED)\*(?=\s|$)""", RegexOption.IGNORE_CASE)
    // Moxfield tags at the end: "#Ramp", "#!Staple".
    private val HASH_TAG = Regex("""\s+#!?[A-Za-z][^#]*$""")

    private val FOIL = Regex("""\s+\*[A-Z]+\*$""")
    // Archidekt-style tags at the end of a line: "[Ramp]", "[Removal,Draw]", "^Have,#37d67a^".
    private val CARET_TAG = Regex("""\s+\^[^^]*\^$""")
    private val BRACKET_TAG = Regex("""\s+\[([^\]]*)\]$""")
    // A set code in brackets is all one case ("[MH2]", "[mh2]"); "[Ramp]" is a tag.
    private val SET_IN_BRACKETS = Regex("""[A-Z0-9]{2,6}|[a-z0-9]{2,6}""")
    private val ARENA_NAME = Regex("""^name\s+.+$""", RegexOption.IGNORE_CASE)
    private val FOIL_MARK = Regex("""\s\*(F|E|FOIL|ETCHED)\*(\s|$)""", RegexOption.IGNORE_CASE)
    // Group headers in exported lists: "Creatures (24)", "Lands", "Ramp (10)".
    private val TYPE_HEADER = Regex(
        """^(creatures?|instants?|sorcery|sorceries|artifacts?|enchantments?|planeswalkers?|lands?|battles?|spells?|others?|non-?lands?|main|mainboard)\s*:?\s*(\(\d+\))?$""",
        RegexOption.IGNORE_CASE,
    )
    private val COUNTED_HEADER = Regex("""^[^()\[\]]+\s\(\d+\)$""")

    fun parse(text: String): List<DeckLine> {
        var section = "Main"
        var sawHeader = false
        val out = mutableListOf<DeckLine>()
        // Index into [out] where each blank-line-separated block starts (for MTGO sideboards).
        val blockStarts = mutableListOf(0)
        for (raw in text.lines()) {
            val line = raw.trim().trimStart('-', '•', '*').trim()
            if (line.isEmpty()) {
                if (blockStarts.last() != out.size) blockStarts += out.size
                continue
            }
            if (line.startsWith("#")) continue
            val header = SECTION.matchEntire(line)
            if (header != null) {
                section = sectionName(header.groupValues[1])
                sawHeader = true
                continue
            }
            if (line.startsWith("//")) continue

            val m = LINE.matchEntire(line)
            val qty: Int
            var rest: String
            var lineSection = section
            if (m != null) {
                qty = (m.groupValues[2].toIntOrNull() ?: 9999).coerceAtMost(9999)
                rest = m.groupValues[3]
                when (m.groupValues[1].uppercase()) {
                    "SB" -> { lineSection = "Sideboard"; sawHeader = true }
                    "CMDR" -> { lineSection = "Commander"; sawHeader = true }
                }
            } else {
                // Arena export preamble: "About" / "Name My Deck".
                if (line.equals("about", ignoreCase = true) || ARENA_NAME.matches(line)) continue
                // "Creatures (24)" and other group headers: not cards, and back in the main deck.
                if (TYPE_HEADER.matches(line) || COUNTED_HEADER.matches(line)) { section = "Main"; continue }
                qty = 1
                rest = line
            }
            val foil = FOIL_MARK.containsMatchIn(" $rest")
            rest = stripTags(rest.replace(FOIL_ANYWHERE, ""))
            // Goldfish's "<251>" is a collector number; "<retro>" and the like are dropped.
            var angleNumber: String? = null
            ANGLE.findAll(rest).forEach { a -> a.groupValues[1].trim().takeIf { NUMBER.matches(it) }?.let { angleNumber = it } }
            rest = rest.replace(ANGLE, "").trim()
            val p = PRINTING.matchEntire(rest)
            val pre = if (p == null) PREFIX_PRINTING.matchEntire(rest) else null
            val entry = when {
                p != null -> DeckLine(
                    qty, p.groupValues[1].trim(), p.groupValues[2].lowercase(), p.groupValues[3].ifEmpty { null } ?: angleNumber, lineSection, foil,
                )
                pre != null -> DeckLine(
                    qty, pre.groupValues[3].trim(), pre.groupValues[1].lowercase(), pre.groupValues[2].ifEmpty { null } ?: angleNumber, lineSection, foil,
                )
                else -> DeckLine(qty, rest, section = lineSection, foil = foil)
            }
            if (entry.name.isNotBlank() && entry.quantity > 0) out += entry
        }
        // MTGO lists: no headers, main deck, a blank line, then the sideboard.
        val starts = blockStarts.filter { it < out.size }
        if (!sawHeader && starts.size >= 2) {
            val start = starts.last()
            val main = out.subList(0, start).sumOf { it.quantity }
            val side = out.subList(start, out.size).sumOf { it.quantity }
            if (main >= 60 && side in 1..15) {
                for (i in start until out.size) out[i] = out[i].copy(section = "Sideboard")
            }
        }
        // Merge duplicates in the same section; different printings (Forest #246 and #247) stay apart.
        return out.groupBy { listOf(it.name.lowercase(), it.section, it.setCode, it.collectorNumber?.lowercase(), it.foil) }
            .map { (_, v) -> v.first().copy(quantity = v.sumOf { it.quantity }) }
    }

    /** Drops foil markers and Archidekt tags from the end, keeping "[CMR]"-style set codes. */
    private fun stripTags(text: String): String {
        var rest = text.trim()
        while (true) {
            val before = rest
            rest = rest.replace(FOIL, "").replace(CARET_TAG, "").replace(HASH_TAG, "").trim()
            val b = BRACKET_TAG.find(rest)
            if (b != null) {
                val inner = b.groupValues[1]
                val left = rest.substring(0, b.range.first).trim()
                // "[CMR]" right after the name is a set code; anything else (or after a printing) is a tag.
                val isSet = SET_IN_BRACKETS.matches(inner) && PRINTING.matchEntire(left) == null
                if (!isSet) rest = left
            }
            if (rest == before || rest.isEmpty()) return rest
        }
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
