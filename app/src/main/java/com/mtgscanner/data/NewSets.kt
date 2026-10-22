package com.mtgscanner.data

import java.time.LocalDate

/** A set from Scryfall's sets list, just what the new-set offer needs. */
data class SetRelease(val code: String, val name: String, val type: String, val releasedAt: String?)

/** New set setup: offers a box or divider and a missing checklist once, when a main set comes out. Pure, for tests. */
object NewSets {
    /** Main releases only: not promos, tokens, Secret Lairs or Alchemy. */
    val TYPES = setOf("expansion", "core", "masters", "draft_innovation")

    /** Released this many days ago or less still counts as new. */
    const val NEW_DAYS = 21L

    /** The newest main set out by [today] within [NEW_DAYS] that wasn't offered before, or null. */
    fun pick(sets: List<SetRelease>, today: LocalDate, offered: Set<String>): SetRelease? =
        sets.mapNotNull { s ->
            val day = s.releasedAt?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
            if (s.type !in TYPES || s.code.lowercase() in offered) return@mapNotNull null
            if (day.isAfter(today) || day.isBefore(today.minusDays(NEW_DAYS))) return@mapNotNull null
            s to day
        }.maxByOrNull { it.second }?.first

    /** Divider text for the set: "FDN · Foundations". */
    fun dividerLabel(s: SetRelease): String = "${s.code.uppercase()} · ${s.name}"
}
