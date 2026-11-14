package com.mtgscanner.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ProxySheetTest {
    @Test fun oneEntryPerCopy() {
        assertEquals(listOf("Bolt", "Bolt", "Opt"), ProxySheet.copies(listOf("Bolt" to 2, "Shock" to 0, "Duress" to -1, "Opt" to 1)))
        assertEquals(emptyList<String>(), ProxySheet.copies(emptyList()))
    }

    @Test fun nineToAPage() {
        assertEquals(0, ProxySheet.pages(0))
        assertEquals(1, ProxySheet.pages(1))
        assertEquals(1, ProxySheet.pages(9))
        assertEquals(2, ProxySheet.pages(10))
        assertEquals(2, ProxySheet.pages(18))
        assertEquals(3, ProxySheet.pages(19))
    }

    @Test fun letterInTheUsAndCanadaA4Elsewhere() {
        assertEquals(612 to 792, ProxySheet.pageSize(Locale.US))
        assertEquals(612 to 792, ProxySheet.pageSize(Locale.CANADA))
        assertEquals(595 to 842, ProxySheet.pageSize(Locale.UK))
        assertEquals(595 to 842, ProxySheet.pageSize(Locale.GERMANY))
    }

    /** Three real cards across must fit both page sizes, or the outer cards are cut off. */
    @Test fun threeCardsFitThePage() {
        for ((w, h) in listOf(612 to 792, 595 to 842)) {
            assertTrue(3 * ProxySheet.CARD_W <= w)
            assertTrue(3 * ProxySheet.CARD_H <= h)
        }
    }
}
