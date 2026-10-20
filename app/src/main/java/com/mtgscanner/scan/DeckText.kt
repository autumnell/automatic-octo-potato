package com.mtgscanner.scan

import com.mtgscanner.util.DecklistParser

/** Edits a pasted decklist in place, one main-deck card at a time, keeping the rest of the text as it was. */
object DeckText {
    private val LEADING_QTY = Regex("""^(\s*[-•*]?\s*)(\d+)(\s*[xX]?\s+)""")
    private val MAIN_HEADER = Regex("""^(//\s*)?(main\s*deck|maindeck|deck|mainboard)\s*:?""", RegexOption.IGNORE_CASE)

    /** Adds [delta] copies of [name] to the main deck (negative takes copies away). */
    fun adjust(text: String, name: String, delta: Int): String {
        if (delta == 0 || name.isBlank()) return text
        if (text.isBlank()) return if (delta > 0) "$delta $name" else text
        val lines = text.lines().toMutableList()
        var section = "Main"
        var found = -1
        var lastMain = -1
        val want = CardTextParser.normalize(name.substringBefore(" // "))
        for ((i, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val parsed = DecklistParser.parse(line)
            if (parsed.isEmpty()) {
                // A section header: see which section the next card would land in.
                val after = DecklistParser.parse("$line\n1 Zz").lastOrNull()?.section ?: section
                if (after != "Main") section = after
                else if (MAIN_HEADER.containsMatchIn(trimmed)) section = "Main"
                continue
            }
            val l = parsed.first()
            val lineSection = if (l.section == "Main") section else l.section
            if (lineSection != "Main") continue
            lastMain = i
            if (found < 0 && CardTextParser.normalize(l.name.substringBefore(" // ")) == want) found = i
        }
        if (found >= 0) {
            val line = lines[found]
            val qty = DecklistParser.parse(line).first().quantity
            val n = qty + delta
            if (n <= 0) lines.removeAt(found)
            else {
                val m = LEADING_QTY.find(line)
                lines[found] = if (m != null) line.replaceRange(m.groups[2]!!.range, n.toString()) else "$n ${line.trim()}"
            }
            return lines.joinToString("\n")
        }
        if (delta < 0) return text
        lines.add(lastMain + 1, "$delta $name")
        return lines.joinToString("\n")
    }

    /** Copies of [name] in the main deck. */
    fun count(text: String, name: String): Int {
        val want = CardTextParser.normalize(name.substringBefore(" // "))
        return DecklistParser.parse(text).filter { it.section == "Main" && CardTextParser.normalize(it.name.substringBefore(" // ")) == want }
            .sumOf { it.quantity }
    }
}
