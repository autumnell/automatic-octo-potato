package com.mtgscanner.util

/** A deck list as text to copy or share, the way Moxfield writes it. Pure, for tests. */
object DeckExport {
    /** Section headers in the order they're written; the main deck has none. Tokens are left out. */
    private val BLOCKS = listOf(
        "Commander" to "COMMANDER:",
        "Companion" to "COMPANION:",
        "Signature" to "SIGNATURE SPELL:",
        "Main" to "",
        "Sideboard" to "SIDEBOARD:",
        "Maybeboard" to "MAYBEBOARD:",
    )

    /** "1 Sol Ring (CMR) 263", "1 Sol Ring (CMR)", "1 Sol Ring", with " *F*" for foil. */
    fun line(l: DeckLine): String = buildString {
        append(l.quantity).append(' ').append(l.name)
        val set = l.setCode?.takeIf { it.isNotBlank() }
        if (set != null) {
            append(" (").append(set.uppercase()).append(')')
            l.collectorNumber?.takeIf { it.isNotBlank() }?.let { append(' ').append(it) }
        }
        if (l.foil) append(" *F*")
    }

    /**
     * Commander first, then the main deck, then sideboard and maybeboard under their headers, a blank line
     * between blocks. Main-deck lines naming one of [commanders] go in the commander block.
     */
    fun moxfield(lines: List<DeckLine>, commanders: List<String> = emptyList()): String {
        val leads = commanders.map(CardNames::key).toSet()
        fun sectionOf(l: DeckLine) = if (l.section == "Main" && CardNames.key(l.name) in leads) "Commander" else l.section
        val bySection = lines.groupBy(::sectionOf)
        return BLOCKS.mapNotNull { (section, header) ->
            val ls = bySection[section].orEmpty()
            if (ls.isEmpty()) null
            else (if (header.isEmpty()) "" else "$header\n") + ls.joinToString("\n", transform = ::line)
        }.joinToString("\n\n")
    }
}
