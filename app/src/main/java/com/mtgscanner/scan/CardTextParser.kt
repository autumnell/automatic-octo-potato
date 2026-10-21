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
    /** Scryfall language code when the card doesn't look English ("de", "ja"), else null. */
    val lang: String? = null,
    /** All text read, top to bottom (capped), for the unknown card queue. */
    val text: String = "",
    /**
     * No name or footer could be read, but a card outline was found: its art window hashed at the likely
     * ways up, to match against downloaded art indexes. Empty otherwise.
     */
    val artOnly: List<Long> = emptyList(),
    /** Embeddings of the same pictures as [artOnly], in the same order, when the art model is ready. */
    val artOnlyEmbeddings: List<ArtVector> = emptyList(),
    /** The copyright year at the bottom of the card ("™ & © 1997 Wizards"), if read. */
    val year: Int? = null,
    /** Embedding of the straightened artwork, filled in by the analyzer when the art model is ready. */
    val artEmbedding: ArtVector? = null,
    /** Shape of the set symbol on the type line, from the straightened card. */
    val symbol: SymbolPrint? = null,
    /** Large blown-out highlights over the name or footer. */
    val glare: Boolean = false,
    /** The name as printed in Japanese, Chinese or Korean, read by a CJK reader when no English name was read. */
    val printedName: String? = null,
) {
    val isEmpty: Boolean get() = nameCandidates.isEmpty() && collectorNumber == null && printedName == null

    /** A key that stays the same while the same card is in view. */
    val key: String?
        get() = when {
            setCode != null && collectorNumber != null -> "${setCode}#${collectorNumber}"
            nameCandidates.isNotEmpty() -> CardTextParser.normalize(nameCandidates.first())
            printedName != null -> "p:" + CjkNames.normalize(printedName)
            else -> null
        }

    /** The name read, English or as printed, to tell frames of the same card apart. */
    val nameKey: String?
        get() = nameCandidates.firstOrNull()?.let { CardTextParser.normalize(it) } ?: printedName?.let { "p:" + CjkNames.normalize(it) }
}

/**
 * Turns raw OCR lines into a card guess. Pure Kotlin so it can be unit tested.
 *
 * Card layout (upright): the name is the top-left line, the type line sits in the middle,
 * and modern cards (2020+) print "0123 R" / "123/280 R" and "SET • EN" in the bottom-left.
 */
object CardTextParser {

    // "123/280", "0045/0281". Power/toughness ("3/3") and "+1/+1" are told apart by the size of the set.
    private val COLLECTOR_FRACTION = Regex("""(?<![\d/+\-])(0*)(\d{1,4})\s*/\s*(0*)(\d{1,4})(?![\d/])""")

    // "R 0123", "C 0045", "M 0200" (rarity letter then number), or "0123 R"
    private val RARITY_THEN_NUMBER = Regex("""(?<![A-Z0-9])[CURMLSTP]\s+0*(\d{1,4}[a-z]?)(?![0-9])""")
    private val NUMBER_THEN_RARITY = Regex("""(?<![A-Z0-9/])0*(\d{1,4}[a-z]?)\s+[CURMLSTP](?![A-Za-z0-9])""")

    // "MOM • EN", OCR often reads the bullet as *, ., -, or drops it. Matched as printed (capitals),
    // so rules text like "loses", "Spirit" or "token" never reads as a footer.
    private val LANGS = "EN|ES|FR|DE|IT|PT|JA|JP|KO|RU|ZHS|ZHT|CS|CT|PH"
    private val SET_LANG = Regex("""(?<![A-Za-z0-9])([A-Z0-9]{3,5})\s*[•·*.\-+]?\s*($LANGS)(?![A-Za-z])""")

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

    /**
     * [footerAnywhere]: every line may hold the footer, for reads of just the name bar and footer strips.
     * Otherwise the footer is only looked for in the lower part of the text, so rules text can't give one.
     */
    fun parse(lines: List<OcrLine>, footerAnywhere: Boolean = false): CardTextGuess {
        val clean = lines.filter { it.text.isNotBlank() }
        if (clean.isEmpty()) return CardTextGuess(emptyList(), null, null, 0)

        val score = clean.sumOf { readableScore(it.text) }
        val (setCode, number) = parseFooter(clean, footerAnywhere)
        val names = nameCandidates(clean)
        val text = clean.sortedBy { it.top }.joinToString("\n") { it.text }.take(600)
        // The language code is printed in the footer only.
        val footerLang = lowerLines(clean).firstNotNullOfOrNull { findLangCode(it.text) }
        // The copyright line is at the very bottom; look there first.
        val year = (lowerLines(clean) + clean).firstNotNullOfOrNull { findCopyrightYear(it.text) }
        return CardTextGuess(
            names, setCode, number, score, findArtBox(clean) ?: estimateArtBox(clean),
            lang = LanguageDetector.detect(text, footerLang), text = text, year = year,
        )
    }

    /**
     * Combines a read of just the name bar and footer ([strips], sharper) with a read of the whole card
     * ([full], with the rules text that tells the language). The strips' name comes first; a complete
     * footer is taken from whichever read has one.
     */
    fun merge(strips: CardTextGuess, full: CardTextGuess): CardTextGuess {
        if (strips.isEmpty) return full.copy(year = full.year ?: strips.year, glare = strips.glare || full.glare)
        // A CJK name from the name bar: the whole card's read can't add a name, only its footer.
        val printed = strips.printedName ?: full.printedName
        // With only a CJK name in the name bar, "names" the English reader made of the rest are noise.
        val names = if (printed != null && strips.nameCandidates.isEmpty()) emptyList()
        else (strips.nameCandidates + full.nameCandidates).distinct().take(3)
        val stripFooter = strips.setCode != null && strips.collectorNumber != null
        val fullFooter = full.setCode != null && full.collectorNumber != null
        val (set, number) = when {
            stripFooter -> strips.setCode to strips.collectorNumber
            fullFooter -> full.setCode to full.collectorNumber
            else -> (strips.setCode ?: full.setCode) to (strips.collectorNumber ?: full.collectorNumber)
        }
        // A language code read in the footer is certain, and so is the script of a CJK name; otherwise the rules
        // text tells more than a name.
        val lang = when {
            strips.setCode != null -> strips.lang
            strips.printedName != null -> strips.lang ?: full.lang
            else -> full.lang ?: strips.lang
        }
        return full.copy(
            nameCandidates = names, setCode = set, collectorNumber = number,
            score = maxOf(strips.score, full.score), lang = lang,
            text = full.text.ifEmpty { strips.text }, year = strips.year ?: full.year,
            glare = strips.glare || full.glare, printedName = printed,
        )
    }

    private const val YEAR = """((?:199[3-9])|(?:20[0-4]\d))"""

    // "©", "(C)", or what OCR makes of the sign: a lone C, O, o, e or @ right before the year.
    private val COPYRIGHT = Regex("""(?:©|\(\s*[cC]\s*\)|(?<![A-Za-z0-9])[cCoOe@])\s*$YEAR(?:\s*[-–—]\s*$YEAR)?(?!\d)""")

    // A year (or range) followed closely by "Wizards", when the sign itself wasn't read.
    private val YEAR_WIZARDS = Regex("""(?<![\d/])$YEAR(?:\s*[-–—]\s*$YEAR)?(?!\d)(?=[^0-9]{0,8}(?:Wizards|WIZARDS|Wizard|WotC|WOTC))""")

    /**
     * The copyright year from a line like "™ & © 1997 Wizards of the Coast". For a range ("©1993-2024 Wizards")
     * the last year is the printing's. Null when no copyright is on the line.
     */
    fun findCopyrightYear(text: String): Int? {
        val m = COPYRIGHT.find(text) ?: YEAR_WIZARDS.find(text) ?: return null
        val last = m.groupValues[2].ifEmpty { m.groupValues[1] }
        return last.toIntOrNull()
    }

    /**
     * Reads a binder page: splits the text into a [rows] x [cols] grid by where each line sits,
     * and reads each pocket as its own card. Returns one guess per pocket, row by row.
     *
     * The grid covers [frame] (where the page guide sits in the image), grown to take in any text
     * outside it. So a page with only some pockets filled still lines up with the right cells.
     * Without a frame, the grid spans the text found.
     */
    fun parseGrid(lines: List<OcrLine>, rows: Int = 3, cols: Int = 3, frame: OcrBox? = null): List<CardTextGuess> {
        val clean = lines.filter { it.text.isNotBlank() }
        if (clean.isEmpty()) return List(rows * cols) { CardTextGuess(emptyList(), null, null, 0) }
        val left = minOf(clean.minOf { it.left }, frame?.left ?: Int.MAX_VALUE)
        val right = maxOf(clean.maxOf { it.right }, frame?.right ?: Int.MIN_VALUE)
        val top = minOf(clean.minOf { it.top }, frame?.top ?: Int.MAX_VALUE)
        val bottom = maxOf(clean.maxOf { it.bottom }, frame?.bottom ?: Int.MIN_VALUE)
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

    /** Where the on-screen page guide falls in an upright image of [width] x [height]: centered, card-shaped, 92% wide. */
    fun pageFrame(width: Int, height: Int): OcrBox {
        val pw = minOf(width * 0.92, height * 0.92 * 63.0 / 88.0)
        val ph = pw * 88.0 / 63.0
        val l = ((width - pw) / 2).toInt()
        val t = ((height - ph) / 2).toInt()
        return OcrBox(l, t, width - l, height - t)
    }

    /** Counts letters in real-looking words. Upside-down or sideways text scores near zero. */
    fun readableScore(text: String): Int =
        text.split(Regex("""\s+"""))
            .filter { w -> w.length >= 3 && w.count { it.isLetter() } >= w.length - 1 }
            .sumOf { it.length }

    /** Lines in the lower part of the text block, left first: where the footer is. */
    private fun lowerLines(lines: List<OcrLine>): List<OcrLine> {
        val minTop = lines.minOf { it.top }
        val maxBottom = lines.maxOf { it.bottom }
        val cutoff = minTop + (maxBottom - minTop) * 0.6
        return lines.filter { it.top >= cutoff }.sortedBy { it.left }
    }

    private fun parseFooter(lines: List<OcrLine>, anywhere: Boolean): Pair<String?, String?> {
        // The footer is in the lower left of the text block (power/toughness sits lower right).
        val lower = lowerLines(lines)
        for (pool in if (anywhere) listOf(lower, lines) else listOf(lower)) {
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

    /**
     * When the type line wasn't read, guesses the art window from the name line and the width of the
     * text below it (rules text spans about the art's width). Old cards with no collector number can
     * then still be told apart by their artwork. Needs a few lines, so a lone name gives nothing.
     */
    fun estimateArtBox(lines: List<OcrLine>): OcrBox? {
        if (lines.size < 3) return null
        val sorted = lines.sortedBy { it.top }
        val nameLine = sorted.firstOrNull { cleanName(it.text) != null } ?: return null
        val below = sorted.filter { it.top > nameLine.bottom }
        if (below.size < 2) return null
        val right = below.maxOf { it.right }
        val width = right - nameLine.left
        if (width < nameLine.height * 8) return null
        val top = nameLine.bottom + nameLine.height / 2
        val height = (width / 1.37).toInt()
        // The art must end above the lowest text read, or the guess is off.
        if (top + height > below.maxOf { it.bottom }) return null
        return OcrBox(nameLine.left, top, right, top + height)
    }

    /** The language code from a footer like "MOM • DE", as printed. */
    fun findLangCode(text: String): String? {
        val m = SET_LANG.find(text) ?: return null
        if (m.groupValues[1].none { it.isLetter() }) return null
        return m.groupValues[2]
    }

    fun findCollectorNumber(text: String): String? {
        for (m in COLLECTOR_FRACTION.findAll(text)) {
            val (zeroA, num, zeroB, total) = m.destructured
            val n = num.toInt()
            val t = total.toInt()
            // A set has at least 20 cards; smaller fractions are power/toughness ("3/3", "1/1").
            // Leading zeros ("0003/0281") are only ever printed in a footer.
            val padded = zeroA.isNotEmpty() || zeroB.isNotEmpty()
            if (padded || (t >= 20 && n != t)) return num
        }
        val upper = text.uppercase()
        RARITY_THEN_NUMBER.find(upper)?.let { return it.groupValues[1].lowercase() }
        NUMBER_THEN_RARITY.find(upper)?.let { return it.groupValues[1].lowercase() }
        return null
    }

    fun findSetCode(text: String): String? {
        val m = SET_LANG.find(text) ?: return null
        val code = m.groupValues[1]
        // A set code has at least one letter; pure numbers are collector numbers.
        if (code.none { it.isLetter() }) return null
        return code
    }

    /**
     * Name candidates from the title line only: lines in the top ~25% of the text, and above the type line
     * when one was read. Lines that read like rules or flavor text are skipped. When the title wasn't read
     * (glare, or cut off at the top) this gives nothing, so the scanner goes by the footer or the art instead.
     */
    private fun nameCandidates(lines: List<OcrLine>): List<String> {
        val minTop = lines.minOf { it.top }
        val maxBottom = lines.maxOf { it.bottom }
        val span = (maxBottom - minTop).coerceAtLeast(1)
        val sorted = lines.sortedWith(compareBy<OcrLine> { it.top }.thenBy { it.left })
        val typeLine = sorted.firstOrNull { isTypeLine(it.text) }
        val topLines = sorted.filter { l ->
            l.top - minTop <= span * 0.25 && (typeLine == null || l.bottom <= typeLine.top + typeLine.height / 2)
        }.filter { it !== typeLine }

        val out = LinkedHashSet<String>()
        for (line in topLines) {
            if (!looksLikeTitle(line.text)) continue
            val name = cleanName(line.text) ?: continue
            out += name
            if (out.size >= 3) break
        }
        return out.toList()
    }

    private val TYPE_ONLY_WORDS = setOf(
        "legendary", "basic", "snow", "world", "tribal", "kindred", "creature", "instant", "sorcery", "artifact",
        "enchantment", "land", "planeswalker", "battle", "token", "ongoing", "elite",
    )

    /**
     * A type line: "Creature — Elf Druid", or only type words ("Instant", "Legendary Artifact"). Names that
     * start with a type word ("Land Tax") aren't, as they don't have the dash and have other words.
     */
    fun isTypeLine(text: String): Boolean {
        val t = text.trim()
        val dash = Regex("""\s[—–-]\s""").find(t)
        val head = if (dash != null) t.substring(0, dash.range.first) else t
        val words = head.lowercase().split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (words.isEmpty()) return false
        return if (dash != null) words.any { it in TYPE_ONLY_WORDS } && words.all { it in TYPE_ONLY_WORDS || it.length <= 2 }
        else words.all { it in TYPE_ONLY_WORDS }
    }

    // Short words a card name can have in lower case ("Jace, the Mind Sculptor", "Will-o'-the-Wisp").
    private val NAME_SMALL_WORDS = setOf(
        "a", "an", "the", "of", "and", "or", "to", "in", "on", "at", "by", "for", "from", "with", "into", "upon",
        "as", "de", "la", "le", "du", "des", "del", "von", "o'", "o", "vs", "s", "is", "it",
    )

    /**
     * Whether a line could be a card title, not rules or flavor text. Titles are in Title Case; flavor text is a
     * quoted sentence and rules text is sentences in lower case. One lower-case word is let through for OCR slips.
     */
    fun looksLikeTitle(raw: String): Boolean {
        val t = raw.trim()
        if (t.isEmpty()) return false
        if (t.first() in "\"“”‘'„«—–") return false
        if (t.endsWith('.') || t.endsWith('”') || t.endsWith('"')) return false
        val words = t.split(Regex("""[\s\-]+""")).map { it.trim(',', ':', '!', '?', '(', ')', '"', '“', '”') }.filter { w -> w.any { it.isLetter() } }
        val lower = words.count { w -> w.first().isLowerCase() && w.lowercase() !in NAME_SMALL_WORDS }
        return lower <= 1
    }

    /** Strips mana symbols and OCR junk from a title line. Returns null if it isn't name-like. */
    fun cleanName(raw: String): String? {
        var s = foldAccents(raw)
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
        foldAccents(name).lowercase().filter { it.isLetterOrDigit() }

    /**
     * Latin letters with accents as plain letters, so "Æther Vial" reads as "Aether Vial" and "Séance" as
     * "Seance" (Scryfall finds both spellings). Other scripts are left alone.
     */
    fun foldAccents(text: String): String {
        if (text.all { it.code < 0x80 }) return text
        val out = StringBuilder(text.length + 4)
        for (c in text) {
            when {
                c.code < 0x80 -> out.append(c)
                c == 'Æ' -> out.append("Ae")
                c == 'æ' -> out.append("ae")
                c == 'Œ' -> out.append("Oe")
                c == 'œ' -> out.append("oe")
                c == 'ß' -> out.append("ss")
                c == 'Ø' -> out.append('O')
                c == 'ø' -> out.append('o')
                c == 'Đ' -> out.append('D')
                c == 'đ' -> out.append('d')
                c == 'Ł' -> out.append('L')
                c == 'ł' -> out.append('l')
                // An accent read as its own character.
                c.code in 0x300..0x36F -> Unit
                c.code in 0xC0..0x24F -> java.text.Normalizer.normalize(c.toString(), java.text.Normalizer.Form.NFD)
                    .forEach { d -> if (d.code !in 0x300..0x36F) out.append(d) }
                else -> out.append(c)
            }
        }
        return out.toString()
    }

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
