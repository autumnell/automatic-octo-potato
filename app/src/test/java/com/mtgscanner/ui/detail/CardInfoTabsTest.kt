package com.mtgscanner.ui.detail

import com.mtgscanner.data.ScryfallCard
import org.junit.Assert.assertEquals
import org.junit.Test

class CardInfoTabsTest {
    private fun print(id: String, set: String, setName: String = set.uppercase()) = ScryfallCard(
        id = id, name = "Bolt", set = set, setName = setName, collectorNumber = id, rarity = "common",
    )

    @Test fun legalityKnownFormatsFirstWithShortNames() {
        val rows = legalityRows(mapOf("zzz" to "legal", "duel" to "banned", "standard" to "not_legal"))
        assertEquals(listOf("Standard" to "not_legal", "Duel Cmdr." to "banned", "Zzz" to "legal"), rows)
    }

    @Test fun groupsBySetAndFilters() {
        val all = listOf(print("1", "m10", "Magic 2010"), print("2", "lea", "Alpha"), print("3", "m10", "Magic 2010"))
        val g = groupPrintings(all, "")
        assertEquals(listOf("m10", "lea"), g.map { it.first })
        assertEquals(2, g[0].second.size)
        assertEquals(listOf("lea"), groupPrintings(all, "alp").map { it.first })
        assertEquals(listOf("m10"), groupPrintings(all, "M10").map { it.first })
    }

    @Test fun limitStopsAcrossGroups() {
        val all = listOf(print("1", "a"), print("2", "a"), print("3", "b"), print("4", "c"))
        val l = limitGroups(groupPrintings(all, ""), 3)
        assertEquals(listOf("a", "b"), l.map { it.first })
        assertEquals(3, l.sumOf { it.second.size })
    }
}
