package com.mtgscanner.work

import com.mtgscanner.data.ScryfallCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WatchMathTest {
    private fun printing(oracle: String, set: String, day: String, promo: Boolean = false) = ScryfallCard(
        id = "$oracle-$set", name = oracle, oracleId = oracle, releasedAt = day, set = set, setName = set,
        collectorNumber = "1", rarity = "rare", promo = promo,
    )

    @Test fun onlyNewBansInDecksSeenBefore() {
        val before = mapOf("1" to listOf("a"))
        val now = mapOf("1" to listOf("a", "b"), "2" to listOf("c"))
        assertEquals(mapOf("1" to listOf("b")), WatchMath.newBans(before, now))
    }

    @Test fun reprintQueriesAreChunked() {
        val q = WatchMath.reprintQueries(List(25) { "id$it" }, "2026-10-01")
        assertEquals(2, q.size)
        assertTrue(q[0].startsWith("(oracleid:id0 or oracleid:id1"))
        assertTrue(q[0].endsWith("date>=2026-10-01 game:paper is:reprint"))
    }

    @Test fun newestNonPromoPrintingSince() {
        val found = listOf(
            printing("a", "old", "2026-01-01"), printing("a", "mkm", "2026-10-10"), printing("a", "pmkm", "2026-10-20", promo = true),
            printing("b", "xyz", "2026-09-01"),
        )
        val r = WatchMath.reprints(found, "2026-10-01")
        assertEquals(setOf("a"), r.keys)
        assertEquals("MKM", r.getValue("a").set)
    }

    @Test fun reprintTagFadesAfterTwoMonths() {
        val s = WatchState(reprints = mapOf("a" to Reprint("MKM", "2026-10-10")))
        assertEquals("Reprint · MKM", WatchStore.tag(s, "a", LocalDate.parse("2026-11-01")))
        assertEquals(null, WatchStore.tag(s, "a", LocalDate.parse("2027-01-01")))
    }
}
