package com.mtgscanner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FrameTest {
    @Test fun artBoxSitsBetweenNameAndTypeLine() {
        val lines = listOf(
            OcrLine("Llanowar Elves", 50, 40, 300, 70),
            OcrLine("Creature — Elf Druid", 50, 400, 320, 430),
        )
        val box = CardTextParser.findArtBox(lines)
        assertNotNull(box)
        assertEquals(70, box!!.top)
        assertEquals(400, box.bottom)
        assertEquals(50, box.left)
        assertEquals(50 + (330 * 1.37).toInt(), box.right)
    }

    @Test fun noArtBoxWithoutTypeLine() {
        assertNull(CardTextParser.findArtBox(listOf(OcrLine("Llanowar Elves", 50, 40, 300, 70))))
    }

    @Test fun textOutsideTheFrameIsDropped() {
        val inside = OcrLine("Sol Ring", 400, 300, 600, 330)
        val edge = OcrLine("Other card", 0, 0, 60, 20)
        assertEquals(listOf(inside), CardTextParser.insideFrame(listOf(inside, edge), 1000, 1000))
    }
}
