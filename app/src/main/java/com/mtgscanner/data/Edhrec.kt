package com.mtgscanner.data

import java.text.Normalizer

/** Links to a commander's page on EDHREC. Pure, for tests. */
object Edhrec {
    private val MARKS = Regex("\\p{Mn}+")
    private val GAPS = Regex("[^a-z0-9]+")

    /**
     * EDHREC's name for a card in its links: front face only, lower case, accents dropped, apostrophes and
     * commas taken out, every other run of characters a "-". "Atraxa, Praetors' Voice" is "atraxa-praetors-voice".
     */
    fun slug(name: String): String {
        val front = name.substringBefore(" // ").trim().lowercase()
            .replace("æ", "ae").replace("œ", "oe").replace("ø", "o").replace("ß", "ss")
        val plain = MARKS.replace(Normalizer.normalize(front, Normalizer.Form.NFD), "")
        return GAPS.replace(plain.replace("'", "").replace("’", "").replace(",", ""), "-").trim('-')
    }

    /**
     * The commander page for [commanders]: one commander's own page, or for partners both names joined
     * in alphabetical order. Null when no name is given.
     */
    fun commanderUrl(commanders: List<String>): String? {
        val slugs = commanders.map(::slug).filter { it.isNotEmpty() }.distinct().take(2).sorted()
        if (slugs.isEmpty()) return null
        return "https://edhrec.com/commanders/" + slugs.joinToString("-")
    }

    /**
     * Where the EDHREC button goes: Scryfall's own EDHREC link for a single commander ([scryfallLink], from
     * related_uris), else [commanderUrl].
     */
    fun linkFor(commanders: List<String>, scryfallLink: String?): String? {
        val names = commanders.filter { it.isNotBlank() }
        if (names.size == 1 && !scryfallLink.isNullOrBlank() && scryfallLink.startsWith("https://")) return scryfallLink
        return commanderUrl(names)
    }
}
