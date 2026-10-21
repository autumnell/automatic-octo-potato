package com.mtgscanner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentNamesTest {

    @Test
    fun foldsLigaturesAndAccents() {
        assertEquals("Aether Vial", CardTextParser.foldAccents("Æther Vial"))
        assertEquals("Seance", CardTextParser.foldAccents("Séance"))
        assertEquals("Lim-Dul's Vault", CardTextParser.foldAccents("Lim-Dûl's Vault"))
        assertEquals("Junun Efreet", CardTextParser.foldAccents("Junún Efreet"))
        // A loose combining accent is dropped.
        assertEquals("Seance", CardTextParser.foldAccents("Séance"))
    }

    @Test
    fun leavesOtherScriptsAlone() {
        assertEquals("稲妻", CardTextParser.foldAccents("稲妻"))
        assertEquals("Lightning Bolt", CardTextParser.foldAccents("Lightning Bolt"))
    }

    @Test
    fun cleanNameKeepsAccentedLetters() {
        assertEquals("Aether Vial", CardTextParser.cleanName("Æther Vial 1"))
        assertEquals("Seance", CardTextParser.cleanName("Séance 2W"))
    }

    @Test
    fun normalizeMatchesBothSpellings() {
        assertEquals(CardTextParser.normalize("Aether Vial"), CardTextParser.normalize("Æther Vial"))
        assertEquals(1.0, CardTextParser.similarity("Seance", "Séance"), 0.0001)
    }

    @Test
    fun accentedTitleReadsAsName() {
        val g = CardTextParser.parse(
            listOf(
                OcrLine("Æther Vial 1", 10, 0, 300, 30),
                OcrLine("Artifact", 10, 300, 300, 320),
                OcrLine("At the beginning of your upkeep, you may put a charge counter on Aether Vial.", 10, 360, 300, 380),
            ),
        )
        assertEquals("Aether Vial", g.nameCandidates.first())
        assertTrue(CardTextParser.readableScore(g.nameCandidates.first()) >= 4)
    }

    @Test
    fun matcherFindsAccentedName() {
        val m = CardNameMatcher(listOf("Séance", "Aether Vial", "Lightning Bolt"))
        assertEquals("Séance", m.best("Seance")?.name)
        assertEquals("Aether Vial", m.best("Æther Vial")?.name)
    }
}
