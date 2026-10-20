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

    @Test fun hideNotLegalOffDropsFormat() {
        assertEquals("", SearchFilters(format = "commander", hideNotLegal = false).toQuery())
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
