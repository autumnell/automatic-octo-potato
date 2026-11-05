package com.mtgscanner.util

/** A mana cost as a screen reader should say it: "{2}{U}{U}" is "2, blue, blue". */
object ManaSpeech {
    private val SYMBOL = Regex("""\{([^}]*)\}|(//)""")
    private val names = mapOf(
        "W" to "white", "U" to "blue", "B" to "black", "R" to "red", "G" to "green", "C" to "colorless",
        "S" to "snow", "X" to "X", "Y" to "Y", "Z" to "Z", "T" to "tap", "Q" to "untap", "E" to "energy", "P" to "Phyrexian",
    )

    private fun one(s: String): String {
        if (s.all { it.isDigit() } && s.isNotEmpty()) return s
        val parts = s.split('/')
        if (parts.size == 2 && parts[1] == "P") return "Phyrexian ${names[parts[0]] ?: parts[0]}"
        if (parts.size >= 2) return parts.joinToString(" or ") { names[it] ?: it }
        return names[s] ?: s
    }

    fun of(cost: String): String {
        val words = SYMBOL.findAll(cost).map { m -> if (m.groupValues[2] == "//") "or" else one(m.groupValues[1]) }.toList()
        return if (words.isEmpty()) cost else words.joinToString(", ").replace(", or, ", " or ")
    }
}
