package com.mtgscanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoxLinksTest {
    @Test
    fun roundTrip() {
        assertEquals("grimoire://box/12", BoxLinks.link(12))
        assertEquals(12L, BoxLinks.boxId("grimoire://box/12"))
        assertEquals(12L, BoxLinks.boxId(" GRIMOIRE://box/12/ "))
        assertEquals("browse/7", BoxLinks.route("grimoire://box/7"))
    }

    @Test
    fun rejectsOthers() {
        assertNull(BoxLinks.boxId("https://example.com/box/1"))
        assertNull(BoxLinks.boxId("grimoire://box/"))
        assertNull(BoxLinks.boxId("grimoire://box/0"))
        assertNull(BoxLinks.boxId(null))
        assertNull(BoxLinks.route("hello"))
    }
}
