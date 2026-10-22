package com.mtgscanner.ui.scan

/** One line of a finished scan list, reduced to what the summary needs. */
data class SummaryLine(
    val name: String,
    val imageUrl: String?,
    /** Price of one copy; null when unknown. */
    val unitPrice: Double?,
    val count: Int,
    /** Box (or deck) it went to; empty when none. */
    val box: String,
)

/** What a scan session added: cards, value, the priciest card and how many went to each box. */
data class ScanSummary(
    val cards: Int,
    val value: Double,
    val best: SummaryLine?,
    /** Box name to cards, most first. */
    val boxes: List<Pair<String, Int>>,
) {
    companion object {
        const val NO_BOX = "No box"

        /** Null when nothing was added, so no summary shows. */
        fun of(lines: List<SummaryLine>): ScanSummary? {
            val added = lines.filter { it.count > 0 }
            if (added.isEmpty()) return null
            val best = added.filter { (it.unitPrice ?: 0.0) > 0.0 }.maxByOrNull { it.unitPrice ?: 0.0 }
            val boxes = added.groupBy { it.box.ifBlank { NO_BOX } }
                .map { (box, ls) -> box to ls.sumOf { it.count } }
                .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
            return ScanSummary(
                cards = added.sumOf { it.count },
                value = added.sumOf { (it.unitPrice ?: 0.0) * it.count },
                best = best,
                boxes = boxes,
            )
        }
    }
}
