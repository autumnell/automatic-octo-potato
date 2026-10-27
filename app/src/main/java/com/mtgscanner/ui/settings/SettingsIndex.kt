package com.mtgscanner.ui.settings

/** Every option on the Settings page, by name, so a search can find it and open its tab. Kept free of Android so it can be tested. */
object SettingsIndex {
    data class Entry(val title: String, val where: String, val tab: Int, val words: String = "")

    val entries = listOf(
        Entry("Theme", "Look", 0, "dark light system mode"),
        Entry("True black", "Look", 0, "amoled oled black background"),
        Entry("Accent color", "Look", 0, "colour red gold custom from my collection"),
        Entry("Fonts", "Look", 0, "headings text prices typeface"),
        Entry("Big text", "Look", 0, "larger size accessibility"),
        Entry("Vibrate on actions", "Look", 0, "haptics buzz vibration feedback"),
        Entry("Show prices", "Prices", 1, "hide value money"),
        Entry("Currency", "Prices", 1, "usd eur euro dollars"),
        Entry("Price alerts", "Prices", 1, "notifications moves daily"),
        Entry("Import CSV", "Data", 2, "manabox moxfield archidekt deckbox"),
        Entry("Export", "Data", 2, "csv insurance pdf"),
        Entry("Back up", "Data", 2, "backup save file zip"),
        Entry("Weekly backup", "Data", 2, "drive folder automatic"),
        Entry("Quiet at night", "Data", 2, "sound notifications sleep"),
        Entry("Card list on phone", "Data", 2, "offline download every printing"),
        Entry("Match by art", "Data", 2, "offline art sets"),
        Entry("Restore backup", "Data", 2, "replace load"),
        Entry("Automatic backups", "Data", 2, "safety copy before delete sort out restore"),
        Entry("Recent actions", "Data", 2, "undo history"),
        Entry("Offline scans", "Data", 2, "pending waiting connection"),
        Entry("Show tour", "Data", 2, "help intro about"),
        Entry("Crash reports", "Data", 2, "bug log error"),
        Entry("Version", "Data", 2, "about build"),
    )

    /** Entries whose name or keywords hold every word typed; names that start with the text come first. */
    fun search(text: String): List<Entry> {
        val words = text.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return entries
            .filter { e -> val hay = "${e.title} ${e.words}".lowercase(); words.all { it in hay } }
            .sortedBy { if (it.title.lowercase().startsWith(words.first())) 0 else 1 }
    }
}
