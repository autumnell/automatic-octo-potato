package com.mtgscanner.ui.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchFiltersTest {
    @Test fun emptyFiltersAddNothing() {
        assertEquals("", SearchFilters().toQuery())
        assertEquals("bolt", SearchFilters().combine(" bolt "))
        assertEquals("cards", SearchFilters().unique)
    }

    @Test fun buildsScryfallSyntax() {
        val f = SearchFilters(format = "modern", lang = "ja", groupPrintings = false)
            .cycleType("Creature")
            .cycleType("Legendary").cycleType("Legendary")
            .withStat(StatTerm("mv", "<", "3"))
            .withStat(StatTerm("pow", ">", "x"))
        assertEquals("f:modern t:creature -t:legendary mv<3 lang:ja", f.toQuery())
        assertEquals("prints", f.unique)
        assertEquals("elf f:modern t:creature -t:legendary mv<3 lang:ja", f.combine("elf"))
    }

    @Test fun colorsUseScryfallOperators() {
        val f = SearchFilters(format = "commander", colors = setOf("U", "W"), colorMatch = com.mtgscanner.ui.collection.ColorMatch.EXACTLY)
        assertEquals("f:commander c=wu", f.toQuery())
        assertEquals(2, f.activeCount)
        val id = SearchFilters(colors = setOf("G"), byIdentity = true)
        assertEquals("id>=g", id.toQuery())
        assertEquals(true, id.narrows)
    }

    @Test fun formatMeansLegal() {
        val f = SearchFilters(format = "commander")
        assertEquals("f:commander", f.toQuery())
        assertEquals(true, f.narrows)
        assertEquals(1, f.activeCount)
    }

    @Test fun orTextIsWrappedWhenFiltersApply() {
        val f = SearchFilters(format = "modern")
        assertEquals("(goblin or elf) f:modern", f.combine("goblin or elf"))
        assertEquals("(goblin OR elf) f:modern", f.combine("goblin OR elf"))
        assertEquals("goblin or elf", SearchFilters().combine("goblin or elf"))
        assertEquals("sword f:modern", f.combine("sword"))
    }

    @Test fun typedSyntaxIsKeptWhole() {
        val f = SearchFilters(format = "modern")
        assertEquals("t:elf -c:g o:\"draw a card\" f:modern", f.combine("t:elf -c:g o:\"draw a card\""))
        assertEquals("!\"Sol Ring\" f:modern", f.combine("!\"Sol Ring\""))
        assertEquals("(t:elf (c:g OR c:b)) f:modern", f.combine("t:elf (c:g OR c:b)"))
        // Curly quotes from a phone keyboard are made straight.
        assertEquals("o:\"draw a card\" f:modern", f.combine("o:“draw a card”"))
        // A quote or ( left open doesn't swallow the filters.
        assertEquals("o:\"draw a\" f:modern", f.combine("o:\"draw a"))
        assertEquals("((t:elf or t:goblin)) f:modern", f.combine("(t:elf or t:goblin"))
        assertEquals("t:\"(\" f:modern", f.combine("t:\"(\""))
        assertEquals("o:\"say \\\"hi\\\"\" f:modern", f.combine("o:\"say \\\"hi\\\"\""))
    }

    @Test fun opSticksBeforeValueTyped() {
        val f = SearchFilters().withStat(StatTerm("mv", "<", ""))
        assertEquals("<", f.stat("mv").op)
        assertEquals("", f.toQuery())
        assertEquals(0, f.activeCount)
        assertEquals("mv<3", f.withStat(f.stat("mv").copy(value = "3")).toQuery())
    }

    @Test fun negativeStarAndInclusiveStats() {
        val f = SearchFilters()
            .withStat(StatTerm("pow", "=", "*"))
            .withStat(StatTerm("tou", "<=", "-1"))
            .withStat(StatTerm("mv", ">=", "7"))
        assertEquals("pow=* tou<=-1 mv>=7", f.toQuery())
        assertEquals("", SearchFilters().withStat(StatTerm("pow", "<", "*")).toQuery())
    }

    @Test fun cleanStatValue() {
        assertEquals("-1.5", SearchFilters.cleanStatValue("-1.5"))
        assertEquals("12", SearchFilters.cleanStatValue("1-2"))
        assertEquals("*", SearchFilters.cleanStatValue("1*"))
        assertEquals("1.52", SearchFilters.cleanStatValue("1.5.2"))
    }

    @Test fun exactNameEscapesQuotes() {
        assertEquals("!\"Henzie \\\"Toolbox\\\" Torre\"", exactNameQuery("Henzie \"Toolbox\" Torre"))
        assertEquals("!\"Shock\"", exactNameQuery("Shock"))
    }

    @Test fun recentSameAs() {
        val a = RecentSearch("Elf", SearchFilters(format = "modern"))
        assertEquals(true, a.sameAs(RecentSearch("elf", SearchFilters(format = "modern"))))
        assertEquals(false, a.sameAs(RecentSearch("elf")))
        assertEquals(false, a.sameAs(RecentSearch("elf", exactName = true)))
        assertEquals("f:modern", a.filterLabel)
    }

    @Test fun cycleGoesOffIsNotOff() {
        val a = SearchFilters().cycleType("Land")
        assertEquals(listOf(TypeTerm("Land")), a.types)
        val b = a.cycleType("land")
        assertEquals(listOf(TypeTerm("Land", not = true)), b.types)
        assertEquals(emptyList<TypeTerm>(), b.cycleType("Land").types)
    }

    @Test fun blankStatRemoves() {
        val f = SearchFilters().withStat(StatTerm("tou", "=", "4"))
        assertEquals("tou=4", f.toQuery())
        assertEquals("", f.withStat(StatTerm("tou", "=", "")).toQuery())
    }
}
