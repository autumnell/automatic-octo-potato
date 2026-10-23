package com.mtgscanner.data

import com.mtgscanner.util.DecklistParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoListTextTest {
    private fun line(text: String, left: Int, top: Int, h: Int = 20) = PhotoListText.Line(text, left, top, left + 100, top + h)

    @Test fun joinsColumnsOnTheSameRow() {
        // Quantities read as their own column, a little out of line with the names.
        val lines = listOf(
            line("Lightning Bolt", 40, 3),
            line("4", 0, 0),
            line("Sol Ring", 40, 41),
            line("1", 0, 40),
            line("Sideboard", 0, 90),
            line("2 Duress", 0, 120),
        )
        assertEquals(listOf("4 Lightning Bolt", "1 Sol Ring", "Sideboard", "2 Duress"), PhotoListText.rows(lines))
        val parsed = DecklistParser.parse(PhotoListText.text(lines))
        assertEquals(listOf(4 to "Lightning Bolt", 1 to "Sol Ring", 2 to "Duress"), parsed.map { it.quantity to it.name })
        assertEquals("Sideboard", parsed.last().section)
    }

    @Test fun tidiesCommonMisreads() {
        assertEquals("1 Sol Ring", PhotoListText.clean("l Sol Ring"))
        assertEquals("1 Sol Ring", PhotoListText.clean("Ix Sol Ring"))
        assertEquals("I Am Inevitable", PhotoListText.clean("I Am Inevitable"))
        assertEquals("4 Lightning Bolt", PhotoListText.clean("• Lightning Bolt x4"))
        assertEquals("4 Lightning Bolt", PhotoListText.clean("Lightning Bolt 4x"))
        assertEquals("3 Opt", PhotoListText.clean("x3 Opt"))
        assertEquals("2 Thoughtseize", PhotoListText.clean("2  Thoughtseize   $12.50"))
        assertEquals("1 Sol Ring (CMR) 263", PhotoListText.clean("1 Sol Ring (CMR) 263"))
        assertNull(PhotoListText.clean("$4.99"))
        assertNull(PhotoListText.clean("12"))
    }
}
