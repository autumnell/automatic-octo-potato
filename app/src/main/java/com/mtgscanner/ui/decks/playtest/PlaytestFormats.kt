package com.mtgscanner.ui.decks.playtest

/** Which opponents a deck may playtest against: only decks of its own format. Pure, for tests. */
object PlaytestFormats {
    /** Your other decks with the same format id ("" matches decks with no format set). */
    fun sameFormat(format: String, other: String): Boolean = format == other

    /**
     * Which precon types fit [format]: Commander takes the Commander precons, constructed formats the 60-card ones
     * (each still checked card by card when picked), and the other commander formats none, since no precon is built
     * for them. A deck with no format can face any precon.
     */
    fun preconFits(format: String, isCommanderPrecon: Boolean, commanderFormat: Boolean): Boolean = when {
        format.isEmpty() -> true
        format == "commander" -> isCommanderPrecon
        commanderFormat -> false
        else -> !isCommanderPrecon
    }
}
