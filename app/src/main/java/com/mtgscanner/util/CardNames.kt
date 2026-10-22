package com.mtgscanner.util

import com.mtgscanner.scan.CardTextParser

/**
 * Names of cards with two halves: split cards (Fire // Ice), adventures, transform and modal double-faced cards,
 * flip cards. Scryfall names them "A // B"; lists also write "A/B", "A / B" or just the front face "A".
 */
object CardNames {
    private val SEP = Regex("""\s*/{1,2}\s*""")

    /** Each half's name: "Fire // Ice" → [Fire, Ice]; a one-faced card → [name]. */
    fun faces(name: String): List<String> = name.split(SEP).map { it.trim() }.filter { it.isNotEmpty() }

    /** The front face: "Queen of Ice // Rage of Winter" → "Queen of Ice". */
    fun front(name: String): String = faces(name).firstOrNull() ?: name.trim()

    /** Scryfall's spelling: "Fire/Ice" and "Fire / Ice" → "Fire // Ice". Other names are only trimmed. */
    fun full(name: String): String = faces(name).ifEmpty { listOf(name.trim()) }.joinToString(" // ")

    fun hasFaces(name: String): Boolean = faces(name).size > 1

    /** Key for matching a card by name: "A // B", "A/B", "A / B" and "A" are the same card. Case and accents ignored. */
    fun key(name: String): String = CardTextParser.normalize(front(name))
}
