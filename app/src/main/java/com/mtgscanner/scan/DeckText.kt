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

    /**
     * Adds [delta] copies of [name] to [board] ("Main", "Sideboard" or "Maybeboard"; negative takes copies away).
     * A section the list doesn't have yet gets its header at the end. With [set] (and [number]) only the lines of that
     * printing change, so a card listed in two printings keeps the other one.
     */
    fun adjust(text: String, name: String, delta: Int, board: String = "Main", set: String? = null, number: String? = null): String {
        if (delta == 0 || name.isBlank()) return text
        val target = board
        if (text.isBlank()) return if (delta <= 0) text else if (target == "Main") "$delta $name" else "$target\n$delta $name"
        val lines = withSideboardHeader(text).lines().toMutableList()
        var section = "Main"
        val found = mutableListOf<Int>()
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
            if (lineSection != target) continue
            lastMain = i
            val samePrinting = set == null || (l.setCode.equals(set, ignoreCase = true) && l.collectorNumber.equals(number, ignoreCase = true))
            if (com.mtgscanner.util.CardNames.key(l.name) == want && samePrinting) found += i
        }
        if (found.isNotEmpty()) {
            if (delta > 0) {
                lines[found.first()] = withQty(lines[found.first()], DecklistParser.parse(lines[found.first()]).first().quantity + delta)
                return lines.joinToString("\n")
            }
            // Taking copies away: from each line of the card in turn (two printings are two lines), last line first.
            var left = -delta
            for (i in found.asReversed()) {
                if (left == 0) break
                val qty = DecklistParser.parse(lines[i]).first().quantity
                val take = minOf(qty, left)
                left -= take
                if (qty - take <= 0) lines.removeAt(i) else lines[i] = withQty(lines[i], qty - take)
            }
            return lines.joinToString("\n")
        }
        if (delta < 0) return text
        if (lastMain < 0 && target != "Main") return text.trimEnd() + "\n\n$target\n$delta $name"
        // Lists that mark sideboard cards with "SB:" instead of a header keep doing so.
        val sb = target == "Sideboard" && lines[lastMain].trim().startsWith("SB:", ignoreCase = true)
        lines.add(lastMain + 1, if (sb) "SB: $delta $name" else "$delta $name")
        return lines.joinToString("\n")
    }

    private fun withQty(line: String, n: Int): String {
        val m = LEADING_QTY.find(line)
        return if (m != null) line.replaceRange(m.groups[2]!!.range, n.toString()) else "$n ${line.trim()}"
    }

    /**
     * An MTGO list (no headers, the sideboard as the last blank-line block) with a "Sideboard" header put before
     * that block, so an edit that adds a header elsewhere doesn't turn the old sideboard into main-deck cards.
     * Other lists come back as they were.
     */
    fun withSideboardHeader(text: String): String {
        if (DecklistParser.parse(text).none { it.section == "Sideboard" }) return text
        val lines = text.lines()
        val explicit = lines.any { line ->
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#")) return@any false
            val parsed = DecklistParser.parse(line)
            if (parsed.isEmpty()) DecklistParser.parse("$line\n1 Zz").lastOrNull()?.section.let { it != null && it != "Main" }
            else parsed.first().section != "Main"
        }
        if (explicit) return text
        // The last blank line that has cards after it starts the sideboard block.
        val lastCard = lines.indexOfLast { it.isNotBlank() }
        val blank = (lastCard downTo 0).firstOrNull { lines[it].isBlank() } ?: return text
        return (lines.subList(0, blank + 1) + "Sideboard" + lines.subList(blank + 1, lines.size)).joinToString("\n")
    }

    /** Moves every copy of [name] from section [from] to [to]. */
    fun move(text: String, name: String, from: String, to: String): String {
        val n = count(text, name, from)
        if (n == 0 || from == to) return text
        return adjust(adjust(text, name, -n, from), name, n, to)
    }

    /** Copies of [name] in [section], the main deck unless named. */
    fun count(text: String, name: String, section: String = "Main"): Int {
        val want = com.mtgscanner.util.CardNames.key(name)
        return DecklistParser.parse(text).filter { it.section == section && com.mtgscanner.util.CardNames.key(it.name) == want }
            .sumOf { it.quantity }
    }
}
