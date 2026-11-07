package com.mtgscanner.scan

/** Something said while scanning, for hands full of cards. */
sealed interface VoiceCommand {
    /** "foil", or "not foil" / "normal". */
    data class Foil(val on: Boolean) : VoiceCommand
    /** "four", "times three", "playset": copies to add. */
    data class Copies(val n: Int) : VoiceCommand
    /** "undo": takes back the last card. */
    data object Undo : VoiceCommand
    /** "set aside": keeps the card on screen to check later. */
    data object SetAside : VoiceCommand
    /** "skip" / "next". */
    data object Skip : VoiceCommand
    /** "add" / "yes": adds the card on screen. */
    data object Add : VoiceCommand
}

/** Turns what the speech recognizer heard into a [VoiceCommand]. Pure, for tests. */
object VoiceCommands {
    private val NUMBERS = mapOf(
        "one" to 1, "two" to 2, "to" to 2, "too" to 2, "three" to 3, "four" to 4, "for" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "playset" to 4,
    )

    /** The command in [heard] (one of the recognizer's guesses), or null when it isn't one. */
    fun parse(heard: String): VoiceCommand? {
        // Apostrophes go without a space, so "it's foil" reads as "its foil" rather than "it s foil".
        val t = heard.lowercase().replace("'", "").replace("’", "").replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
        if (t.isEmpty()) return null
        val words = t.split(' ')
        return when {
            t in setOf("not foil", "non foil", "nonfoil", "no foil", "normal", "regular", "not a foil") -> VoiceCommand.Foil(false)
            t == "foil" || t == "it's foil" || t == "its foil" || t == "foil it" -> VoiceCommand.Foil(true)
            t == "undo" || t == "take it back" || t == "take back" -> VoiceCommand.Undo
            t == "set aside" || t == "aside" || t == "set it aside" || t == "check later" -> VoiceCommand.SetAside
            t == "skip" || t == "next" || t == "skip it" -> VoiceCommand.Skip
            t == "add" || t == "add it" || t == "yes" -> VoiceCommand.Add
            else -> copies(words)
        }
    }

    /** "four", "4", "times four", "four copies", "playset". Bare "to" and "for" are too common to count alone. */
    private fun copies(words: List<String>): VoiceCommand? {
        val rest = words.filter { it !in setOf("times", "copies", "copy", "x", "of", "them", "make", "it") }
        if (rest.size != 1) return null
        val w = rest[0]
        if (words.size == 1 && (w == "to" || w == "for" || w == "too")) return null
        val n = w.toIntOrNull() ?: NUMBERS[w] ?: return null
        return if (n in 1..99) VoiceCommand.Copies(n) else null
    }

    /** The first command among the recognizer's guesses, most likely first. */
    fun first(guesses: List<String>): VoiceCommand? = guesses.firstNotNullOfOrNull { parse(it) }
}
