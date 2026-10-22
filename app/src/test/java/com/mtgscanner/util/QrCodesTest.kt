package com.mtgscanner.util

import com.mtgscanner.data.BoxLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrCodesTest {
    @Test
    fun labelCodeReadsBack() {
        val text = BoxLinks.link(42)
        val m = QrCodes.modules(text)!!
        val scale = 6
        val pad = 20
        val w = m.size * scale + 2 * pad
        val h = w + 10
        val stride = w + 16
        // White frame with the code drawn in black, padded like a camera row.
        val y = ByteArray(stride * h) { 0xF0.toByte() }
        for (r in m.indices) for (c in m.indices) if (m[r][c]) {
            for (dy in 0 until scale) for (dx in 0 until scale) y[(pad + r * scale + dy) * stride + pad + c * scale + dx] = 0x10
        }
        assertEquals(text, QrCodes.decode(y, stride, w, h))
        assertEquals(42L, BoxLinks.boxId(QrCodes.decode(y, stride, w, h)))
    }

    @Test
    fun blankFrameHasNoCode() {
        assertNull(QrCodes.decode(ByteArray(100 * 100) { 0x7F }, 100, 100, 100))
    }
}
