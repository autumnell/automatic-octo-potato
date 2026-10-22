package com.mtgscanner.data

/**
 * Tokens, emblems and Art Series cards: printed and collected, but not cards you play. Told apart by Scryfall's
 * layout when it's known, else by the type line kept on a collection row. Pure Kotlin for tests.
 */
object CardExtras {
    /** Scryfall layouts of tokens, emblems and art cards. */
    val LAYOUTS = setOf("token", "double_faced_token", "emblem", "art_series")

    /** Set types Scryfall gives token and art card sets ("tmom", "amh1"). */
    val SET_TYPES = setOf("token", "memorabilia")

    /** "Token" among the words before the dash ("Token Creature — Soldier", "Legendary Token Artifact"). */
    private val TOKEN_WORD = Regex("""(^|\s)Token(\s|$)""")

    /**
     * Whether a card is a token, an emblem or an art card. [typeLine] as Scryfall gives it (both faces joined by
     * " // "); art cards have "Card" there.
     */
    fun isExtra(typeLine: String, layout: String? = null): Boolean {
        if (layout != null && layout in LAYOUTS) return true
        return typeLine.split(" // ").any { face ->
            val t = face.trim()
            val head = t.substringBefore('—').trim()
            TOKEN_WORD.containsMatchIn(head) || head.startsWith("Emblem") || t == "Card"
        }
    }

    fun isExtra(card: ScryfallCard): Boolean = isExtra(card.effectiveTypeLine, card.layout)
}
