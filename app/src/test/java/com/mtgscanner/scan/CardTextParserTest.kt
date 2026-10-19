package com.mtgscanner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardTextParserTest {

    private fun line(text: String, top: Int, left: Int = 10, height: Int = 20) =
        OcrLine(text, left, top, left + 300, top + height)

    @Test
    fun readsModernCard() {
        val lines = listOf(
            line("Lightning Bolt R", 0, height = 30),
            line("Instant", 300),
            line("Lightning Bolt deals 3 damage to any target.", 360),
            line("0141/0281 C", 560),
            line("2X2 • EN Christopher Moeller", 580),
        )
        val g = CardTextParser.parse(lines)
        assertEquals("Lightning Bolt", g.nameCandidates.first())
        assertEquals("141", g.collectorNumber)
        assertEquals("2X2", g.setCode)
        assertEquals("2X2#141", g.key)
    }

    @Test
    fun readsRarityThenNumberFooter() {
        val lines = listOf(
            line("Sheoldred, the Apocalypse 2BB", 0),
            line("Legendary Creature — Phyrexian Praetor", 300),
            line("Deathtouch", 340),
            line("M 0107", 560),
            line("DMU * EN", 580),
        )
        val g = CardTextParser.parse(lines)
        assertEquals("Sheoldred, the Apocalypse", g.nameCandidates.first())
        assertEquals("107", g.collectorNumber)
        assertEquals("DMU", g.setCode)
    }

    @Test
    fun oldCardHasNameOnly() {
        val lines = listOf(
            line("Serra Angel", 0),
            line("Summon Angel", 300),
            line("Flying, does not tap when attacking", 350),
            line("Illus. Douglas Shuler", 560),
        )
        val g = CardTextParser.parse(lines)
        assertEquals("Serra Angel", g.nameCandidates.first())
        assertNull(g.collectorNumber)
        assertNull(g.setCode)
    }

    @Test
    fun stripsManaJunk() {
        assertEquals("Counterspell", CardTextParser.cleanName("Counterspell UU"))
        assertEquals("Llanowar Elves", CardTextParser.cleanName("Llanowar Elves {G}"))
        assertNull(CardTextParser.cleanName("2R"))
        assertNull(CardTextParser.cleanName("Creature"))
    }

    @Test
    fun garbageScoresLow() {
        assertTrue(CardTextParser.readableScore("Lightning Bolt deals damage") > CardTextParser.readableScore("ʇloq 6uᴉuʇɥƃᴉl 3 ;"))
    }

    @Test
    fun similarityToleratesOcrSlips() {
        assertTrue(CardTextParser.similarity("Lightnlng Bolt", "Lightning Bolt") > 0.85)
        assertTrue(CardTextParser.similarity("Serra Angel", "Shivan Dragon") < 0.5)
    }

    @Test
    fun emptyInput() {
        val g = CardTextParser.parse(emptyList())
        assertTrue(g.isEmpty)
        assertNull(g.key)
    }
}
