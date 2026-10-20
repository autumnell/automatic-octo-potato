package com.mtgscanner.scan

/** One line of text found by OCR, with its bounding box in the (rotated) image. */
data class OcrLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val height: Int get() = bottom - top
}

/** A rectangle in image pixels. */
data class OcrBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** What we could read off a card in one camera frame. */
data class CardTextGuess(
    /** Likely card names, best first. */
    val nameCandidates: List<String>,
    /** Three to five character set code such as "MOM", if the footer was readable. */
    val setCode: String?,
    /** Collector number such as "123" or "45a", if the footer was readable. */
    val collectorNumber: String?,
    /** How much readable English text was found. Used to pick the upright orientation. */
    val score: Int,
    /** Where the artwork probably is: between the name line and the type line. */
    val artBox: OcrBox? = null,
    /** Hash of the artwork, filled in by the analyzer when [artBox] is known. */
    val artHash: Long? = null,
) {
    val isEmpty: Boolean get() = nameCandidates.isEmpty() && collectorNumber == null

    /** A key that stays the same while the same card is in view. */
    val key: String?
        get() = when {
            setCode != null && collectorNumber != null -> "${setCode}#${collectorNumber}"
            nameCandidates.isNotEmpty() -> CardTextParser.normalize(nameCandidates.first())
            else -> null
        }
}

/**
 * Turns raw OCR lines into a card guess. Pure Kotlin so it can be unit tested.
 *
 * Card layout (upright): the name is the top-left line, the type line sits in the middle,
 * and modern cards (2020+) print "0123 R" / "123/280 R" and "SET • EN" in the bottom-left.
 */
object CardTextParser {

    // "123/280", "0045/0281"
    private val COLLECTOR_FRACTION = Regex("""(?<![\d/])0*(\d{1,4})\s*/\s*\d{1,4}(?![\d/])""")

    // "R 0123", "C 0045", "M 0200" (rarity letter then number), or "0123 R"
    private val RARITY_THEN_NUMBER = Regex("""(?<![A-Z0-9])[CURMLSTP]\s+0*(\d{1,4}[a-z]?)(?![0-9])""")
    private val NUMBER_THEN_RARITY = Regex("""(?<![A-Z0-9/])0*(\d{1,4}[a-z]?)\s+[CURMLSTP](?![A-Za-z0-9])""")

    // "MOM • EN", OCR often reads the bullet as *, ., -, or drops it.
    private val LANGS = "EN|ES|FR|DE|IT|PT|JA|JP|KO|RU|ZHS|ZHT|CS|CT|PH"
    private val SET_LANG = Regex("""(?<![A-Z0-9])([A-Z0-9]{3,5})\s*[•·*.\-+]?\s*(?:$LANGS)(?![A-Z])""")

    // Words that are never part of a card name but often show up near the top.
    private val NOISE_WORDS = setOf(
        "illus", "tm", "wizards", "coast", "llc", "creature", "instant", "sorcery",
        "artifact", "enchantment", "land", "planeswalker", "battle", "legendary", "basic",
        "token", "the gathering", "magic",
    )

    /** Lines whose centre lies in the middle [fraction] of a [width] x [height] image. */
    fun insideFrame(lines: List<OcrLine>, width: Int, height: Int, fraction: Double = 0.86): List<OcrLine> {
        val mx = width * (1 - fraction) / 2
        val my = height * (1 - fraction) / 2
        return lines.filter { l ->
            val cx = (l.left + l.right) / 2.0
            val cy = (l.top + l.bottom) / 2.0
            cx in mx..(width - mx) && cy in my..(height - my)
        }
    }

    fun parse(lines: List<OcrLine>): CardTextGuess {
        val clean = lines.filter { it.text.isNotBlank() }
        if (clean.isEmpty()) return CardTextGuess(emptyList(), null, null, 0)

        val score = clean.sumOf { readableScore(it.text) }
        val (setCode, number) = parseFooter(clean)
        val names = nameCandidates(clean)
        return CardTextGuess(names, setCode, number, score, findArtBox(clean))
    }

    /**
     * Reads a binder page: splits the text into a [rows] x [cols] grid by where each line sits,
     * and reads each pocket as its own card. The grid spans the text found, so the page doesn't
     * have to fill the camera exactly. Returns one guess per pocket, row by row.
     */
    fun parseGrid(lines: List<OcrLine>, rows: Int = 3, cols: Int = 3): List<CardTextGuess> {
        val clean = lines.filter { it.text.isNotBlank() }
        if (clean.isEmpty()) return List(rows * cols) { CardTextGuess(emptyList(), null, null, 0) }
        val left = clean.minOf { it.left }
        val right = clean.maxOf { it.right }
        val top = clean.minOf { it.top }
        val bottom = clean.maxOf { it.bottom }
        val cellW = (right - left).coerceAtLeast(1) / cols.toDouble()
        val cellH = (bottom - top).coerceAtLeast(1) / rows.toDouble()
        val cells = List(rows * cols) { mutableListOf<OcrLine>() }
        for (l in clean) {
            val cx = (l.left + l.right) / 2.0
            val cy = (l.top + l.bottom) / 2.0
            val c = ((cx - left) / cellW).toInt().coerceIn(0, cols - 1)
            val r = ((cy - top) / cellH).toInt().coerceIn(0, rows - 1)
            cells[r * cols + c] += l
        }
        return cells.map { parse(it) }
    }

    /** Counts letters in real-looking words. Upside-down or sideways text scores near zero. */
    fun readableScore(text: String): Int =
        text.split(Regex("""\s+"""))
            .filter { w -> w.length >= 3 && w.count { it.isLetter() } >= w.length - 1 }
            .sumOf { it.length }

    private fun parseFooter(lines: List<OcrLine>): Pair<String?, String?> {
        // The footer is in the lower part of the text block. Look there first, then anywhere.
        val minTop = lines.minOf { it.top }
        val maxBottom = lines.maxOf { it.bottom }
        val cutoff = minTop + (maxBottom - minTop) * 0.6
        val lower = lines.filter { it.top >= cutoff }
        for (pool in listOf(lower, lines)) {
            val number = pool.firstNotNullOfOrNull { findCollectorNumber(it.text) }
            val set = pool.firstNotNullOfOrNull { findSetCode(it.text) }
            if (number != null || set != null) return set to number
        }
        return null to null
    }

    private val TYPE_WORDS = Regex(
        """^(Legendary|Basic|Snow|World|Tribal|Kindred|Creature|Instant|Sorcery|Artifact|Enchantment|Land|Planeswalker|Battle)\b""",
    )

    /** The art sits between the title line and the type line on every modern and classic frame. */
    fun findArtBox(lines: List<OcrLine>): OcrBox? {
        val sorted = lines.sortedBy { it.top }
        val nameLine = sorted.firstOrNull { cleanName(it.text) != null } ?: return null
        val typeLine = sorted.firstOrNull { l ->
            l.top > nameLine.bottom && (l.text.contains('—') || TYPE_WORDS.containsMatchIn(l.text.trim()))
        } ?: return null
        val top = nameLine.bottom
        val bottom = typeLine.top
        val height = bottom - top
        // The art window is about 1.37 times as wide as it is tall and starts where the name does.
        val left = minOf(nameLine.left, typeLine.left)
        val box = OcrBox(left, top, left + (height * 1.37).toInt(), bottom)
        // It's much taller than a line of text.
        if (height < nameLine.height * 3) return null
        return box
    }

    fun findCollectorNumber(text: String): String? {
        COLLECTOR_FRACTION.find(text)?.let { return it.groupValues[1] }
        val upper = text.uppercase()
        RARITY_THEN_NUMBER.find(upper)?.let { return it.groupValues[1].lowercase() }
        NUMBER_THEN_RARITY.find(upper)?.let { return it.groupValues[1].lowercase() }
        return null
    }

    fun findSetCode(text: String): String? {
        val m = SET_LANG.find(text.uppercase()) ?: return null
        val code = m.groupValues[1]
        // A set code has at least one letter; pure numbers are collector numbers.
        if (code.none { it.isLetter() }) return null
        return code
    }

    private fun nameCandidates(lines: List<OcrLine>): List<String> {
        val minTop = lines.minOf { it.top }
        val maxBottom = lines.maxOf { it.bottom }
        val span = (maxBottom - minTop).coerceAtLeast(1)
        // Names live in the top ~25% of the card's text.
        val topLines = lines
            .filter { it.top - minTop <= span * 0.25 }
            .sortedWith(compareBy<OcrLine> { it.top }.thenBy { it.left })

        val out = LinkedHashSet<String>()
        for (line in topLines) {
            val name = cleanName(line.text) ?: continue
            out += name
            if (out.size >= 3) break
        }
        // Fallback: tallest plausible lines anywhere (title text is big).
        if (out.isEmpty()) {
            lines.sortedByDescending { it.height }.forEach { line ->
                if (out.size < 2) cleanName(line.text)?.let { out += it }
            }
        }
        return out.toList()
    }

    /** Strips mana symbols and OCR junk from a title line. Returns null if it isn't name-like. */
    fun cleanName(raw: String): String? {
        var s = raw
            .replace('’', '\'')
            .replace(Regex("""\{[^}]*\}"""), " ")
            .replace(Regex("""[^A-Za-z0-9',\- ]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        // Drop trailing tokens that are mana-cost leftovers: digits, single letters like "R", "2U".
        val tokens = s.split(' ').toMutableList()
        while (tokens.isNotEmpty() && isManaJunk(tokens.last())) tokens.removeAt(tokens.lastIndex)
        while (tokens.isNotEmpty() && isManaJunk(tokens.first()) && tokens.first().length == 1) tokens.removeAt(0)
        s = tokens.joinToString(" ").trim(' ', '-', ',')

        val letters = s.count { it.isLetter() }
        if (letters < 3) return null
        if (s.count { it.isDigit() } > 2) return null
        if (s.length > 45) return null
        if (NOISE_WORDS.any { s.lowercase() == it }) return null
        if (s.lowercase().startsWith("illus")) return null
        return s
    }

    private fun isManaJunk(token: String): Boolean {
        if (token.isEmpty()) return true
        if (token.all { it.isDigit() }) return true
        // Mana leftovers are short and all-caps (e.g. "2R", "UU"); real name words are not.
        return token.length <= 4 && token.all { it.isDigit() || it in "WUBRGCX" }
    }

    fun normalize(name: String): String =
        name.lowercase().filter { it.isLetterOrDigit() }

    /** 0..1 similarity based on edit distance, case and punctuation ignored. */
    fun similarity(a: String, b: String): Double {
        val x = normalize(a)
        val y = normalize(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        val d = levenshtein(x, y)
        return 1.0 - d.toDouble() / maxOf(x.length, y.length)
    }

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}
