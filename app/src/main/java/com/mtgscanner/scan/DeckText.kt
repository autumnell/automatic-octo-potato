package com.mtgscanner.scan

import com.mtgscanner.util.CardNames
import com.mtgscanner.util.DeckLine
import com.mtgscanner.util.DecklistParser

/** Edits a pasted decklist in place, one main-deck card at a time, keeping the rest of the text as it was. */
object DeckText {
    private val LEADING_QTY = Regex("""^(\s*[-•*]?\s*)(\d+)(\s*[xX]?\s+)""")
    private val MAIN_HEADER = Regex("""^(//\s*)?(main\s*deck|maindeck|deck|mainboard)\s*:?""", RegexOption.IGNORE_CASE)
    // What comes before the name: a bullet, "SB: ", the count.
    private val LEAD = Regex("""^\s*[-•*]?\s*(?:(?:SB|CMDR):\s*)?(?:\d+\s*[xX]?\s+)?""", RegexOption.IGNORE_CASE)
    private val FOIL_MARK = Regex("""\*(F|E|FOIL|ETCHED)\*""", RegexOption.IGNORE_CASE)
    private val CMDR_MARK = Regex("""\*CMDR\*""", RegexOption.IGNORE_CASE)

    /**
     * Sets the printing of each line that lists [name] as ([oldSet], [oldNumber]) to "(SET) number", keeping the
     * line's count, "SB:" prefix, foil and commander marks. Other lines stay as they were.
     */
    fun setPrinting(text: String, name: String, oldSet: String?, oldNumber: String?, set: String, number: String): String {
        val want = CardNames.key(name)
        return text.lines().joinToString("\n") { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) return@joinToString line
            val l = DecklistParser.parse(line).singleOrNull() ?: return@joinToString line
            val same = CardNames.key(l.name) == want && l.setCode.equals(oldSet, ignoreCase = true) &&
                l.collectorNumber.equals(oldNumber, ignoreCase = true)
            if (same) withPrinting(line, l, set, number) else line
        }
    }

    private fun withPrinting(line: String, l: DeckLine, set: String, number: String): String = buildString {
        append(LEAD.find(line)?.value.orEmpty())
        append(l.name).append(" (").append(set.uppercase()).append(") ").append(number)
        if (l.foil) append(' ').append(FOIL_MARK.find(line)?.value ?: "*F*")
        if (CMDR_MARK.containsMatchIn(line)) append(" *CMDR*")
        tags(line).forEach { append(' ').append(it) }
    }

    private val END_MARK = Regex("""\s+\*[A-Za-z]+\*$""")
    private val END_CARET = Regex("""\s+\^[^^]*\^$""")
    private val END_HASH = Regex("""\s+#!?[A-Za-z][^#*^\[\]]*$""")
    private val END_BRACKET = Regex("""\s+\[([^\]]*)\]$""")
    // "[CMR]" right after the name is a set code, not a tag.
    private val SET_LIKE = Regex("""(?:[A-Z0-9]{2,6}|[a-z0-9]{2,6})(?:[:#][A-Za-z0-9\-★†]+)?""")
    private val HAS_PRINTING = Regex("""[\(\[][A-Za-z0-9]{2,6}(?:[:#][^\)\]]*)?[\)\]]""")

    /** Moxfield and Archidekt tags at the end of a line ("#Ramp", "#!Staple", "^Have,#37d67a^", "[Removal]"), in order. */
    fun tags(line: String): List<String> {
        var rest = line.trimEnd()
        val out = ArrayDeque<String>()
        while (true) {
            val mark = END_MARK.find(rest)
            if (mark != null) { rest = rest.substring(0, mark.range.first); continue }
            val m = END_CARET.find(rest) ?: END_HASH.find(rest) ?: END_BRACKET.find(rest)?.takeIf { b ->
                !SET_LIKE.matches(b.groupValues[1]) || HAS_PRINTING.containsMatchIn(rest.substring(0, b.range.first))
            } ?: break
            out.addFirst(m.value.trim())
            rest = rest.substring(0, m.range.first)
        }
        return out.toList()
    }

    /** Adds [delta] copies of [name] to the main deck (negative takes copies away). */
    fun adjust(text: String, name: String, delta: Int): String {
        if (delta == 0 || name.isBlank()) return text
        if (text.isBlank()) return if (delta > 0) "$delta $name" else text
        val lines = text.lines().toMutableList()
        var section = "Main"
        var found = -1
        var lastMain = -1
        val want = com.mtgscanner.util.CardNames.key(name)
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
            if (found < 0 && com.mtgscanner.util.CardNames.key(l.name) == want) found = i
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
        val want = com.mtgscanner.util.CardNames.key(name)
        return DecklistParser.parse(text).filter { it.section == "Main" && com.mtgscanner.util.CardNames.key(it.name) == want }
            .sumOf { it.quantity }
    }
}
