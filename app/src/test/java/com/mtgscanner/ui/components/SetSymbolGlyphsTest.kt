package com.mtgscanner.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetSymbolGlyphsTest {
    @Test fun knownSets() {
        assertEquals("", SetSymbolGlyphs.glyph("lea"))
        assertEquals("", SetSymbolGlyphs.glyph("LCI"))
        assertEquals("", SetSymbolGlyphs.glyph(" dsk "))
    }

    @Test fun promoAndTokenFallBackToParent() {
        assertEquals(SetSymbolGlyphs.glyph("lci"), SetSymbolGlyphs.glyph("plci"))
        assertEquals(SetSymbolGlyphs.glyph("mkm"), SetSymbolGlyphs.glyph("tmkm"))
    }

    @Test fun unknownIsNull() {
        assertNull(SetSymbolGlyphs.glyph("zzzz"))
        assertNull(SetSymbolGlyphs.glyph(""))
        assertNull(SetSymbolGlyphs.glyph(null))
    }

    @Test fun mapIsFull() {
        assertTrue(SetSymbolGlyphs.count > 400)
    }
}
