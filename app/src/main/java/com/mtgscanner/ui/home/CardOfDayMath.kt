package com.mtgscanner.ui.home

import kotlin.random.Random

/** Picks the card shown as "Card of the day": random, but the same one all day. */
object CardOfDayMath {
    /**
     * Today's pick from [ids], seeded by [epochDay]. [kept] is the (day, id) picked earlier; it stays while it's
     * from today and still owned, so adding or moving cards doesn't swap it mid-day. Null when there are no cards.
     */
    fun pick(ids: Collection<Long>, epochDay: Long, kept: Pair<Long, Long>? = null): Long? {
        if (ids.isEmpty()) return null
        if (kept != null && kept.first == epochDay && kept.second in ids) return kept.second
        val sorted = ids.distinct().sorted()
        return sorted[Random(epochDay * 7919L + 17L).nextInt(sorted.size)]
    }
}
