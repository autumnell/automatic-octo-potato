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

    @Test fun binderPageSplitsIntoPockets() {
        // Two cards side by side on the top row, one on the bottom left.
        val lines = listOf(
            OcrLine("Lightning Bolt", 0, 0, 200, 20), OcrLine("Instant", 0, 150, 120, 165), OcrLine("123/280 C", 0, 280, 100, 295),
            OcrLine("Counterspell", 330, 0, 520, 20), OcrLine("Instant", 330, 150, 450, 165),
            OcrLine("Llanowar Elves", 660, 0, 880, 20),
            OcrLine("Giant Growth", 0, 320, 190, 340), OcrLine("Instant", 0, 470, 120, 485),
            OcrLine("Shock", 0, 640, 100, 660), OcrLine("Shock", 660, 640, 760, 660), OcrLine("x", 900, 940, 910, 950),
        )
        val pockets = CardTextParser.parseGrid(lines)
        assertEquals(9, pockets.size)
        assertEquals("Lightning Bolt", pockets[0].nameCandidates.first())
        assertEquals("123", pockets[0].collectorNumber)
        assertEquals("Counterspell", pockets[1].nameCandidates.first())
        assertEquals("Llanowar Elves", pockets[2].nameCandidates.first())
        assertEquals("Giant Growth", pockets[3].nameCandidates.first())
        assertTrue(pockets[4].isEmpty)
    }

    @Test fun partlyFilledPageUsesTheFrame() {
        // Only the top row of a 900 x 1260 page is filled. The grid must still be a third of the page tall.
        val frame = OcrBox(0, 0, 900, 1260)
        val lines = listOf(
            OcrLine("Lightning Bolt", 10, 10, 200, 30), OcrLine("Instant", 10, 230, 120, 250), OcrLine("123/280 C", 10, 380, 110, 400),
            OcrLine("Counterspell", 310, 10, 500, 30), OcrLine("Instant", 310, 230, 430, 250),
        )
        val pockets = CardTextParser.parseGrid(lines, frame = frame)
        assertEquals("Lightning Bolt", pockets[0].nameCandidates.first())
        assertEquals("123", pockets[0].collectorNumber)
        assertEquals("Counterspell", pockets[1].nameCandidates.first())
        assertTrue(pockets[3].isEmpty)
    }

    @Test fun pageFrameIsCentered() {
        val f = CardTextParser.pageFrame(720, 1280)
        assertTrue(kotlin.math.abs((720 - f.right) - f.left) <= 1)
        assertTrue(f.height > f.width)
    }

    // Real rules text must not read as a footer (B1, B2).

    @Test fun rulesTextIsNotAFooter() {
        val lines = listOf(
            line("Serra Angel 3WW", 0, height = 30),
            line("Creature — Angel", 300),
            line("Target creature loses flying until end of turn.", 340),
            line("Create a 1/1 white Spirit creature token with flying.", 370),
            line("Whenever this effect gives a creature protection, copies it.", 400),
            line("3/3", 560, left = 250),
        )
        val g = CardTextParser.parse(lines)
        assertEquals("Serra Angel", g.nameCandidates.first())
        assertNull(g.setCode)
        assertNull(g.collectorNumber)
        assertNull(g.lang)
        assertEquals("serraangel", g.key)
    }

    @Test fun rulesWordsAreNotLanguageCodes() {
        listOf(
            "Target creature loses flying", "Create a 1/1 white Spirit creature token", "Draw a card, except",
            "It gains protection from the color of your choice", "Infect", "The creature can't block. Evade it.",
        ).forEach {
            assertNull(it, CardTextParser.findLangCode(it))
            assertNull(it, CardTextParser.findSetCode(it))
        }
    }

    @Test fun powerToughnessIsNotACollectorNumber() {
        assertNull(CardTextParser.findCollectorNumber("3/3"))
        assertNull(CardTextParser.findCollectorNumber("Create a 1/1 white Spirit creature token"))
        assertNull(CardTextParser.findCollectorNumber("Target creature gets +2/+2 until end of turn"))
        assertNull(CardTextParser.findCollectorNumber("Create Marit Lage, a legendary 20/20 black Avatar"))
        assertEquals("123", CardTextParser.findCollectorNumber("123/281 R"))
        assertEquals("3", CardTextParser.findCollectorNumber("0003/0281 C"))
        assertEquals("301", CardTextParser.findCollectorNumber("301/280 M"))
    }

    @Test fun readsFooterNextToPowerToughness() {
        val lines = listOf(
            line("Grizzly Bears 1G", 0, height = 30),
            line("Creature — Bear", 300),
            line("Create a 1/1 white Spirit creature token.", 340),
            line("123/281 R", 560, left = 10),
            line("MOM • EN Artist Name", 580, left = 10),
            line("3/3", 570, left = 400),
        )
        val g = CardTextParser.parse(lines)
        assertEquals("123", g.collectorNumber)
        assertEquals("MOM", g.setCode)
        assertEquals("MOM#123", g.key)
        assertNull(g.lang)
    }

    @Test fun readsForeignFooterLanguage() {
        val lines = listOf(
            line("Engel von Serra 3WW", 0, height = 30),
            line("Kreatur — Engel", 300),
            line("Fliegend, Wachsamkeit", 340),
            line("0035/0281 U", 560),
            line("MOM • DE Artist Name", 580),
        )
        val g = CardTextParser.parse(lines)
        assertEquals("de", g.lang)
        assertEquals("MOM", g.setCode)
        assertEquals("35", g.collectorNumber)
    }
}
