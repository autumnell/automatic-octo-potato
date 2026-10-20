package com.mtgscanner.scan

/**
 * Spots non-English cards from what the camera read. Pure Kotlin so it can be unit tested.
 *
 * The footer's language code ("MOM • DE") wins when it was read. Otherwise the text is checked for
 * Japanese, Korean, Chinese and Cyrillic letters, then for common rules-text words in German,
 * French, Italian, Spanish and Portuguese. Returns a Scryfall language code, or null for English
 * or when it can't tell.
 */
object LanguageDetector {

    /** Footer codes as printed, to Scryfall's codes. */
    private val FOOTER = mapOf(
        "EN" to "en", "ES" to "es", "SP" to "es", "FR" to "fr", "DE" to "de", "IT" to "it", "PT" to "pt",
        "JA" to "ja", "JP" to "ja", "KO" to "ko", "RU" to "ru",
        "ZHS" to "zhs", "CS" to "zhs", "ZHT" to "zht", "CT" to "zht", "PH" to "ph",
    )

    fun fromFooter(code: String?): String? = code?.let { FOOTER[it.uppercase()] }

    // Words common in rules text and rare in the other languages (and in English).
    private val WORDS: Map<String, Set<String>> = mapOf(
        "de" to setOf(
            "der", "die", "das", "und", "eine", "einen", "deine", "dein", "deinen", "kreatur", "kreaturen", "karte",
            "bis", "zum", "ende", "zuges", "wenn", "ziehe", "verursacht", "schaden", "fliegend", "ist", "auf", "ein",
            "nicht", "oder", "mit", "sie", "jede", "jeder", "ziel", "spieler",
        ),
        "fr" to setOf(
            "le", "les", "des", "une", "vous", "votre", "vos", "carte", "jusqu", "fin", "tour", "quand", "piochez",
            "blessures", "vol", "cible", "chaque", "sur", "est", "qui", "pas", "du", "au", "joueur", "créatures",
        ),
        "it" to setOf(
            "il", "gli", "di", "che", "creatura", "fino", "alla", "pesca", "danno", "volare", "bersaglio", "ogni",
            "tuo", "tua", "della", "sul", "non", "giocatore", "creature", "carta",
        ),
        "es" to setOf(
            "el", "los", "las", "del", "hasta", "cuando", "roba", "daño", "vuela", "objetivo", "tu", "su", "sus",
            "por", "con", "jugador", "criaturas", "obtiene",
        ),
        "pt" to setOf(
            "os", "uma", "até", "você", "seu", "sua", "compre", "dano", "voar", "alvo", "do", "da", "em", "ao",
            "não", "jogador", "criaturas", "recebe",
        ),
        "en" to setOf(
            "the", "and", "of", "you", "your", "creature", "creatures", "card", "cards", "until", "end", "turn",
            "when", "whenever", "draw", "damage", "flying", "target", "each", "this", "control", "with", "from",
            "player", "gets", "deals",
        ),
    )

    fun detect(text: String, footerCode: String? = null): String? {
        fromFooter(footerCode)?.let { return if (it == "en") null else it }
        fromScript(text)?.let { return it }
        return fromWords(text)
    }

    /** Japanese, Korean, Chinese or Russian letters. Needs a few of them, so a stray symbol doesn't count. */
    fun fromScript(text: String): String? {
        var kana = 0
        var hangul = 0
        var han = 0
        var cyrillic = 0
        for (c in text) {
            when (c.code) {
                in 0x3040..0x30FF -> kana++
                in 0xAC00..0xD7AF, in 0x1100..0x11FF -> hangul++
                in 0x4E00..0x9FFF -> han++
                in 0x0400..0x04FF -> cyrillic++
            }
        }
        return when {
            kana >= 2 -> "ja"
            hangul >= 2 -> "ko"
            han >= 3 -> "zhs"
            cyrillic >= 4 -> "ru"
            else -> null
        }
    }

    fun fromWords(text: String): String? {
        val words = text.lowercase().split(Regex("""[^\p{L}]+""")).filter { it.isNotEmpty() }
        if (words.size < 4) return null
        val scores = WORDS.mapValues { (_, set) -> words.count { it in set } }
        val (best, score) = scores.maxByOrNull { it.value } ?: return null
        if (best == "en" || score < 3) return null
        val runnerUp = scores.filterKeys { it != best }.values.maxOrNull() ?: 0
        return if (score >= runnerUp + 2) best else null
    }
}
