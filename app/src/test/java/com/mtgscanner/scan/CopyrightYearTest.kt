package com.mtgscanner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CopyrightYearTest {
    private fun year(s: String) = CardTextParser.findCopyrightYear(s)

    @Test fun readsThePrintedYear() {
        assertEquals(1997, year("™ & © 1997 Wizards of the Coast, Inc."))
        assertEquals(2024, year("TM & © 2024 Wizards of the Coast"))
        assertEquals(2003, year("©2003 Wizards"))
    }

    @Test fun rangeTakesTheLastYear() {
        assertEquals(2024, year("©1993-2024 Wizards of the Coast"))
        assertEquals(2015, year("TM & © 1993 – 2015 Wizards of the Coast LLC"))
    }

    @Test fun ocrMisreadsOfTheSign() {
        assertEquals(1998, year("TM & (C) 1998 Wizards of the Coast"))
        assertEquals(2001, year("TM & C 2001 Wizards of the Coast"))
        assertEquals(1999, year("™ & O1999 Wizards of the Coast"))
        assertEquals(2007, year("TM & e 2007 Wizards of the Coast, Inc. 45/301"))
    }

    @Test fun yearBeforeWizardsWithoutTheSign() {
        assertEquals(2010, year("TM & 2010 Wizards of the Coast"))
    }

    @Test fun otherNumbersAreNotYears() {
        assertNull(year("Illus. Mark Poole"))
        assertNull(year("0123/0280 R"))
        assertNull(year("Draw 2000 cards"))
        assertNull(year("the 1997 card"))
        assertNull(year("© 1850 Wizards"))
    }

    @Test fun parseFillsTheYearFromTheBottomLine() {
        val lines = listOf(
            OcrLine("Serra Angel", 20, 10, 200, 30),
            OcrLine("Creature — Angel", 20, 300, 200, 320),
            OcrLine("Flying", 20, 340, 120, 360),
            OcrLine("Illus. Douglas Shuler", 40, 520, 260, 535),
            OcrLine("TM & © 1997 Wizards of the Coast, Inc.", 30, 540, 300, 555),
        )
        val g = CardTextParser.parse(lines)
        assertEquals(1997, g.year)
        assertEquals("Serra Angel", g.nameCandidates.first())
    }

    @Test fun mergeKeepsStripNameAndFullLanguage() {
        val strips = CardTextGuess(listOf("Blitzschlag"), null, null, 11, year = 2010)
        val full = CardTextGuess(listOf("Blitzschiag"), null, null, 40, lang = "de", text = "Blitzschlag verursacht 3 Schadenspunkte")
        val m = CardTextParser.merge(strips, full)
        assertEquals("Blitzschlag", m.nameCandidates.first())
        assertEquals(listOf("Blitzschlag", "Blitzschiag"), m.nameCandidates)
        assertEquals("de", m.lang)
        assertEquals(2010, m.year)
        assertEquals(40, m.score)
    }

    @Test fun mergeTakesACompleteFooter() {
        val strips = CardTextGuess(listOf("Lightning Bolt"), "M10", "146", 13, lang = null)
        val full = CardTextGuess(listOf("Lightning Bolt"), null, "999", 30, lang = "fr")
        val m = CardTextParser.merge(strips, full)
        assertEquals("M10", m.setCode)
        assertEquals("146", m.collectorNumber)
        // The footer read "EN" (no language): that wins over a guess from the text.
        assertNull(m.lang)
        val m2 = CardTextParser.merge(CardTextGuess(listOf("Bolt"), null, null, 4), CardTextGuess(emptyList(), "A25", "141", 9))
        assertEquals("A25", m2.setCode)
        assertEquals("141", m2.collectorNumber)
    }

    @Test fun emptyStripsLeaveTheFullRead() {
        val full = CardTextGuess(listOf("Shock"), null, null, 20)
        val m = CardTextParser.merge(CardTextGuess(emptyList(), null, null, 0, glare = true), full)
        assertEquals(listOf("Shock"), m.nameCandidates)
        assertEquals(true, m.glare)
    }
}
