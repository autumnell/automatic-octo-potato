package com.mtgscanner.util

/**
 * Reads a price the user typed. Pure Kotlin, so it can be tested without a phone.
 *
 * Accepts "4.99", "4,99", "$12", "12 €", "1,200", "1.200,50" and "1,234.50":
 * - with both marks, the last one is the decimal mark and the other separates thousands;
 * - a lone comma is a thousands mark with exactly 3 digits after it ("1,200"), unless the number starts
 *   with "0," ("0,125"); otherwise it's decimal ("4,99"). A lone dot is always decimal ("1.5");
 * - a mark used more than once separates thousands ("1,200,000");
 * - spaces between digits are ignored ("1 200,50"). CSV import reads prices with this too.
 */
object PriceInput {
    /** The amount, or null when [text] is blank, negative or not a price. */
    fun parse(text: String): Double? {
        val t = text.trim()
            .removePrefix("$").removePrefix("€")
            .removeSuffix("$").removeSuffix("€")
            .trim()
            .filterNot { it == ' ' || it == '\u00A0' || it == '\u202F' }
        if (t.isEmpty() || t.any { !it.isDigit() && it != ',' && it != '.' }) return null
        val lastComma = t.lastIndexOf(',')
        val lastDot = t.lastIndexOf('.')
        val commas = t.count { it == ',' }
        val dots = t.count { it == '.' }
        val decimal: Char? = when {
            commas > 0 && dots > 0 -> if (lastComma > lastDot) ',' else '.'
            commas == 1 -> if (t.length - lastComma - 1 == 3 && t.substring(0, lastComma) != "0") null else ','
            dots == 1 -> '.'
            else -> null
        }
        val point = if (decimal == null) -1 else t.lastIndexOf(decimal)
        val whole = if (point < 0) t else t.substring(0, point)
        val fraction = if (point < 0) "" else t.substring(point + 1)
        if (fraction.any { !it.isDigit() }) return null
        // Thousands groups: the first 1–3 digits, then groups of exactly 3.
        val groups = whole.split(',', '.')
        if (groups.size > 1 && (groups.first().isEmpty() || groups.first().length > 3 || groups.drop(1).any { it.length != 3 })) return null
        val digits = groups.joinToString("")
        if (digits.isEmpty() && fraction.isEmpty()) return null
        val v = "${digits.ifEmpty { "0" }}.${fraction.ifEmpty { "0" }}".toDoubleOrNull() ?: return null
        return v.takeIf { it.isFinite() && it >= 0 }
    }
}
