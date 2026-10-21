package com.mtgscanner.scan

/** The CJK text readers, each a separate ML Kit model. Each also reads Latin letters. */
enum class CjkScript { JAPANESE, CHINESE, KOREAN }

/**
 * Card names printed in Japanese, Chinese or Korean, read by a CJK text reader. The offline name list is English,
 * so these are matched against Scryfall's printed names instead, with their own tolerance. Pure Kotlin for tests.
 */
object CjkNames {
    /** Scryfall language codes printed in these scripts. */
    val LANGS = setOf("ja", "zhs", "zht", "ko")

    fun isKana(c: Char): Boolean = c.code in 0x3040..0x30FF || c.code in 0x31F0..0x31FF
    fun isHangul(c: Char): Boolean = c.code in 0xAC00..0xD7AF || c.code in 0x1100..0x11FF || c.code in 0x3130..0x318F
    fun isHan(c: Char): Boolean = c.code in 0x4E00..0x9FFF || c.code in 0x3400..0x4DBF || c.code in 0xF900..0xFAFF

    fun isCjk(c: Char): Boolean = isKana(c) || isHangul(c) || isHan(c)

    /** The reader to try first for the language picked in the scan bar, or null for a language that isn't CJK. */
    fun scriptFor(lang: String?): CjkScript? = when (lang?.lowercase()) {
        "ja" -> CjkScript.JAPANESE
        "zhs", "zht" -> CjkScript.CHINESE
        "ko" -> CjkScript.KOREAN
        else -> null
    }

    /**
     * The language of a name read by [script]: the footer's code when read ([footerLang], a Scryfall code), else
     * what the letters say (kana is Japanese, hangul Korean). Han characters alone are Japanese when the Japanese
     * reader found them, else Chinese: traditional when the scan bar says so.
     */
    fun langOf(script: CjkScript, text: String, footerLang: String? = null, scanLang: String? = null): String {
        footerLang?.lowercase()?.takeIf { it in LANGS }?.let { return it }
        if (text.any { isKana(it) }) return "ja"
        if (text.any { isHangul(it) }) return "ko"
        return when (script) {
            CjkScript.JAPANESE -> "ja"
            CjkScript.KOREAN -> "ko"
            CjkScript.CHINESE -> if (scanLang?.lowercase() == "zht") "zht" else "zhs"
        }
    }

    /**
     * A name from a line of the name bar, or null when it isn't one: CJK letters (and the "・" between words and
     * Korean spaces) kept, mana symbols, digits and stray Latin letters dropped. At least two CJK letters.
     */
    fun cleanName(raw: String): String? {
        val kept = StringBuilder()
        for (c in raw) {
            when {
                isCjk(c) -> kept.append(c)
                c == '・' || c == '･' || c == '·' -> kept.append('・')
                c == ' ' || c == '　' -> kept.append(' ')
                else -> kept.append(' ')
            }
        }
        val s = kept.toString().replace(Regex("""\s+"""), " ").trim(' ', '・')
        // Leftover single letters around the name (a mana symbol read as one) are dropped.
        val words = s.split(' ').filter { w -> w.count { isCjk(it) } >= 1 }
        val name = words.joinToString(" ").trim()
        val n = name.count { isCjk(it) }
        if (n < 2 || n > 24) return null
        return name
    }

    /** Name candidates from the name bar's lines (top to bottom, left to right), best first. */
    fun names(lines: List<OcrLine>): List<String> =
        lines.sortedWith(compareBy<OcrLine> { it.top }.thenBy { it.left })
            .mapNotNull { cleanName(it.text) }
            .sortedByDescending { n -> n.count { isCjk(it) } }
            .distinct()
            .take(2)

    /** The letters that count when comparing names: CJK and other letters or digits, no spaces or "・". */
    fun normalize(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    /** 0..1 likeness of two names by edit distance over their letters. */
    fun similarity(a: String, b: String): Double {
        val x = normalize(a)
        val y = normalize(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        return 1.0 - CardNameMatcher.boundedLevenshtein(x, y, maxOf(x.length, y.length) + 1).toDouble() / maxOf(x.length, y.length)
    }

    /**
     * How alike a read name and a printed name must be. Each CJK letter carries much more than a Latin one, so
     * one wrong letter in a short name is a different name: two letters must match exactly, three allow one wrong.
     */
    fun minScore(length: Int): Double = when {
        length <= 2 -> 1.0
        length == 3 -> 0.66
        else -> 0.6
    }

    /**
     * Text to search Scryfall's printed names for: the whole name, then (for longer names, in case one letter was
     * misread) its first and second halves. A search is a substring match, so a half still finds the card.
     */
    fun searchTerms(name: String): List<String> {
        val n = normalize(name)
        if (n.length < 2) return emptyList()
        val out = LinkedHashSet<String>()
        out += name.trim()
        if (n.length >= 4) {
            val half = (n.length + 1) / 2
            out += n.substring(0, half)
            out += n.substring(n.length - half)
        }
        return out.toList()
    }

    /**
     * The candidate whose printed name is closest to [read], with its likeness, when it's close enough
     * ([minScore]). A printed name containing the read one whole (a cut-off read) counts too, if most of it was read.
     */
    fun <T> best(read: String, candidates: List<Pair<T, String?>>): Pair<T, Double>? {
        val r = normalize(read)
        if (r.length < 2) return null
        var bestItem: T? = null
        var bestScore = -1.0
        for ((item, printed) in candidates) {
            val p = normalize(printed ?: continue)
            if (p.isEmpty()) continue
            var s = similarity(r, p)
            if (p.contains(r) && r.length * 4 >= p.length * 3) s = maxOf(s, 0.9)
            if (s > bestScore) { bestScore = s; bestItem = item }
        }
        val item = bestItem ?: return null
        return if (bestScore >= minScore(r.length)) item to bestScore else null
    }

    /** The scripts to try, in order, starting after [last] fails: so one extra read per frame cycles through them. */
    fun next(last: CjkScript): CjkScript = CjkScript.entries[(last.ordinal + 1) % CjkScript.entries.size]
}
