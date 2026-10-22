package com.mtgscanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoxRemindersTest {
    private val m = BoxReminders.MONTH_MS

    @Test
    fun dueOldestFirst() {
        val now = 100 * m
        val checks = mapOf(1L to now - 4 * m, 2L to now - m, 3L to now - 7 * m)
        assertEquals(listOf(3L to 7, 1L to 4), BoxReminders.due(listOf(1, 2, 3, 4), checks, now, 3))
        assertTrue(BoxReminders.due(listOf(1, 2, 3), checks, now, 0).isEmpty())
        assertEquals(listOf(4L), BoxReminders.unseen(listOf(1, 4), checks))
    }

    @Test
    fun labelsAndCodec() {
        assertEquals("Rares: not checked in 3 months", BoxReminders.label("Rares", 3))
        assertEquals("Rares: not checked in a month", BoxReminders.label("Rares", 1))
        val map = mapOf(1L to 5L, 22L to 1700000000000L)
        assertEquals(map, BoxReminders.decode(BoxReminders.encode(map)))
        assertEquals(emptyMap<Long, Long>(), BoxReminders.decode(""))
    }
}
