package com.mtgscanner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CjkNamesTest {
    @Test fun cleansNameBarLines() {
        assertEquals("稲妻", CjkNames.cleanName("稲妻 R"))
        // Mana cost and stray marks around the name are dropped; the dot between words stays.
        assertEquals("ボーラスの城塞", CjkNames.cleanName("ボーラスの城塞 3 B"))
        assertEquals("ジェイス・ベレレン", CjkNames.cleanName("ジェイス・ベレレン 1UU"))
        assertEquals("번개 화살", CjkNames.cleanName("번개 화살 {R}"))
        // English, one letter, or nothing CJK is not a CJK name.
        assertNull(CjkNames.cleanName("Lightning Bolt"))
        assertNull(CjkNames.cleanName("稲 2"))
        assertNull(CjkNames.cleanName(""))
    }

    @Test fun namesFromTheNameBar() {
        val lines = listOf(OcrLine("R", 600, 10, 620, 40), OcrLine("稲妻", 20, 12, 120, 44))
        assertEquals(listOf("稲妻"), CjkNames.names(lines))
        assertEquals(emptyList<String>(), CjkNames.names(listOf(OcrLine("Shock", 0, 0, 80, 30))))
    }

    @Test fun scriptForTheScanBarLanguage() {
        assertEquals(CjkScript.JAPANESE, CjkNames.scriptFor("ja"))
        assertEquals(CjkScript.CHINESE, CjkNames.scriptFor("zhs"))
        assertEquals(CjkScript.CHINESE, CjkNames.scriptFor("ZHT"))
        assertEquals(CjkScript.KOREAN, CjkNames.scriptFor("ko"))
        assertNull(CjkNames.scriptFor("en"))
        assertNull(CjkNames.scriptFor(null))
    }

    @Test fun languageFromWhatWasRead() {
        // Kanji alone, read by the Japanese reader, is Japanese; by the Chinese reader, Chinese.
        assertEquals("ja", CjkNames.langOf(CjkScript.JAPANESE, "稲妻"))
        assertEquals("zhs", CjkNames.langOf(CjkScript.CHINESE, "闪电击"))
        assertEquals("zht", CjkNames.langOf(CjkScript.CHINESE, "閃電擊", scanLang = "zht"))
        // Kana or hangul tell for themselves.
        assertEquals("ja", CjkNames.langOf(CjkScript.CHINESE, "ボーラスの城塞"))
        assertEquals("ko", CjkNames.langOf(CjkScript.JAPANESE, "번개 화살"))
        // The footer's code wins.
        assertEquals("zht", CjkNames.langOf(CjkScript.JAPANESE, "稲妻", footerLang = "zht"))
        assertEquals("ja", CjkNames.langOf(CjkScript.JAPANESE, "稲妻", footerLang = "en"))
    }

    @Test fun searchTermsSplitLongNames() {
        assertEquals(listOf("稲妻"), CjkNames.searchTerms("稲妻"))
        assertEquals(listOf("ボーラスの城塞", "ボーラス", "スの城塞"), CjkNames.searchTerms("ボーラスの城塞"))
        assertEquals(emptyList<String>(), CjkNames.searchTerms("稲"))
    }

    @Test fun matchesPrintedNamesWithCjkTolerance() {
        val cards = listOf("bolt" to "稲妻", "shock" to "ショック", "fortress" to "ボーラスの城塞", "none" to null)
        assertEquals("bolt" to 1.0, CjkNames.best("稲妻", cards))
        // One letter misread in a seven-letter name still matches.
        val fortress = CjkNames.best("ボーラスの城寒", cards)
        assertEquals("fortress", fortress?.first)
        assertTrue(fortress!!.second < 1.0)
        // In a two-letter name one wrong letter is another name.
        assertNull(CjkNames.best("稲光", cards))
        // A read cut short at the end still finds the printed name holding it.
        assertEquals("fortress", CjkNames.best("ボーラスの城", cards)?.first)
        assertNull(CjkNames.best("", cards))
    }

    @Test fun readersTakeTurns() {
        assertEquals(CjkScript.CHINESE, CjkNames.next(CjkScript.JAPANESE))
        assertEquals(CjkScript.KOREAN, CjkNames.next(CjkScript.CHINESE))
        assertEquals(CjkScript.JAPANESE, CjkNames.next(CjkScript.KOREAN))
    }

    @Test fun printedNameKeysTheGuess() {
        val g = CardTextGuess(emptyList(), null, null, 0, printedName = "稲妻")
        assertEquals("p:稲妻", g.key)
        assertEquals("p:稲妻", g.nameKey)
        assertTrue(!g.isEmpty)
        // The strips' CJK name survives merging with the whole card's read, whose English "names" are noise.
        val full = CardTextGuess(listOf("Iilli"), "STA", null, 10, lang = "zhs")
        val merged = CardTextParser.merge(g.copy(lang = "ja"), full)
        assertEquals("稲妻", merged.printedName)
        assertEquals(emptyList<String>(), merged.nameCandidates)
        assertEquals("ja", merged.lang)
        assertEquals("STA", merged.setCode)
    }
}
