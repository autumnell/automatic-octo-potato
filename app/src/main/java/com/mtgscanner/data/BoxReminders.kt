package com.mtgscanner.data

/** "Rares: not checked in 3 months": boxes whose last Box check is older than the chosen interval. Pure, for tests. */
object BoxReminders {
    const val MONTH_MS = 30L * 24 * 60 * 60 * 1000

    /** Settings choices, in months; 0 is Off. */
    val CHOICES = listOf(0, 1, 3, 6)

    /**
     * Boxes due a check, longest wait first, as (box id, whole months since). [checks] holds each box's last check,
     * or the day the app first saw it. Boxes missing from [checks] aren't due yet.
     */
    fun due(boxIds: List<Long>, checks: Map<Long, Long>, now: Long, months: Int): List<Pair<Long, Int>> {
        if (months <= 0) return emptyList()
        return boxIds.mapNotNull { id ->
            val at = checks[id] ?: return@mapNotNull null
            val since = ((now - at) / MONTH_MS).toInt()
            if (now - at >= months * MONTH_MS) id to since else null
        }.sortedByDescending { it.second }
    }

    /** "Rares: not checked in 3 months". */
    fun label(name: String, months: Int): String =
        "$name: not checked in " + if (months <= 1) "a month" else "$months months"

    /** Ids that need a first-seen date: boxes with no record yet. */
    fun unseen(boxIds: List<Long>, checks: Map<Long, Long>): List<Long> = boxIds.filter { it !in checks }

    fun encode(checks: Map<Long, Long>): String = checks.entries.joinToString(",") { "${it.key}:${it.value}" }

    fun decode(s: String?): Map<Long, Long> = s.orEmpty().split(',').mapNotNull { pair ->
        val id = pair.substringBefore(':').toLongOrNull() ?: return@mapNotNull null
        val at = pair.substringAfter(':', "").toLongOrNull() ?: return@mapNotNull null
        id to at
    }.toMap()
}
