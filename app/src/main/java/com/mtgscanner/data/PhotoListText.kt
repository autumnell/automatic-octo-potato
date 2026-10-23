package com.mtgscanner.data

import kotlin.math.abs
import kotlin.math.max

/**
 * Turns the text read off a photo of a printed list (a decklist, a trade or want list) into decklist text, one
 * card per line, for the paste box. Pure, for tests.
 */
object PhotoListText {
    /** One line of text found in the photo and where it is. */
    data class Line(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int)

    private val SPACES = Regex("""\s+""")
    private val BULLET = Regex("""^[\-•·*☐□☑✓✔>]+\s*""")
    // "l Sol Ring", "Ix Sol Ring", "|x Sol Ring": a 1 read as a letter. A lone "I" may start a name, so it needs the x.
    private val ONE_AS_LETTER = Regex("""^(?:[l|][xX]?|I[xX])\s+(?=\S)""")
    // "x4 Sol Ring".
    private val X_FIRST = Regex("""^[xX]\s?(\d{1,3})\s+""")
    // "Lightning Bolt x4", "Lightning Bolt 4x".
    private val QTY_LAST = Regex("""^(.+?)\s+(?:[xX]\s?(\d{1,3})|(\d{1,3})\s?[xX])$""")
    private val PRICE_LAST = Regex("""\s+(?:[$€£]\s?\d+(?:[.,]\d{1,2})?|\d+[.,]\d{2}\s?[$€£]?)$""")

    /** Decklist text from the lines found in a photo; empty when nothing reads as a card. */
    fun text(lines: List<Line>): String = rows(lines).mapNotNull(::clean).joinToString("\n")

    /** Lines on the same row (middles within half a line height) joined left to right; rows top to bottom. */
    fun rows(lines: List<Line>): List<String> {
        val sorted = lines.filter { it.text.isNotBlank() }.sortedBy { it.top + it.bottom }
        val rows = mutableListOf<MutableList<Line>>()
        for (l in sorted) {
            val row = rows.lastOrNull()
            if (row != null) {
                val mid = row.map { (it.top + it.bottom) / 2.0 }.average()
                val h = max(row.map { (it.bottom - it.top).toDouble() }.average(), (l.bottom - l.top).toDouble())
                if (abs((l.top + l.bottom) / 2.0 - mid) <= h / 2) {
                    row += l
                    continue
                }
            }
            rows += mutableListOf(l)
        }
        return rows.map { r -> r.sortedBy { it.left }.joinToString(" ") { it.text.trim() } }
    }

    /** One row tidied for the decklist reader, or null when it has no card name in it. */
    fun clean(row: String): String? {
        var s = row.replace(SPACES, " ").trim()
        s = BULLET.replace(s, "").trim()
        s = ONE_AS_LETTER.replace(s, "1 ")
        s = X_FIRST.replace(s) { "${it.groupValues[1]} " }
        while (true) {
            val t = PRICE_LAST.replace(s, "").trim()
            if (t == s) break
            s = t
        }
        QTY_LAST.matchEntire(s)?.let { m ->
            val n = m.groupValues[2].ifEmpty { m.groupValues[3] }
            val name = m.groupValues[1]
            if (!name.first().isDigit()) s = "$n $name"
        }
        if (s.count { it.isLetter() } < 2) return null
        return s
    }
}
