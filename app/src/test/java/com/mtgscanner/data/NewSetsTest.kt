package com.mtgscanner.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NewSetsTest {
    private val today = LocalDate.of(2026, 10, 21)
    private val sets = listOf(
        SetRelease("aaa", "Old", "expansion", "2026-08-01"),
        SetRelease("bbb", "Fresh", "expansion", "2026-10-10"),
        SetRelease("ccc", "Promo", "promo", "2026-10-18"),
        SetRelease("ddd", "Soon", "expansion", "2026-11-14"),
        SetRelease("eee", "Masters", "masters", "2026-10-03"),
    )

    @Test
    fun picksNewestMainSet() {
        assertEquals("bbb", NewSets.pick(sets, today, emptySet())?.code)
        assertEquals("eee", NewSets.pick(sets, today, setOf("bbb"))?.code)
        assertNull(NewSets.pick(sets, today, setOf("bbb", "eee")))
        assertEquals("BBB · Fresh", NewSets.dividerLabel(sets[1]))
    }
}
