package com.mtgscanner.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BoxCheckTest {
    private fun row(id: Long, sid: String, qty: Int, foil: Boolean = false, oracle: String = "o-$sid", name: String = sid) =
        BoxRow(id, sid, oracle, name, foil, qty)

    private fun scan(sid: String, count: Int = 1, foil: Boolean = false, oracle: String = "o-$sid", name: String = sid) =
        BoxScan(sid, oracle, name, foil, count)

    @Test
    fun splitsFoundMissingAndNotRecorded() {
        val rows = listOf(row(1, "bolt", 3), row(2, "elves", 1))
        val scans = listOf(scan("bolt", 2), scan("forest"))
        val r = BoxCheck.compare(rows, scans)
        assertEquals(2, r.foundCount)
        assertEquals(listOf(1L to 1, 2L to 1), r.missing.map { it.first.rowId to it.second })
        assertEquals(listOf("forest" to 1), r.notRecorded.map { it.first.scryfallId to it.second })
    }

    @Test
    fun otherPrintingOfSameCardCounts() {
        val rows = listOf(row(1, "bolt-m10", 1, oracle = "bolt"))
        val scans = listOf(scan("bolt-2x2", oracle = "bolt"))
        val r = BoxCheck.compare(rows, scans)
        assertEquals(1, r.foundCount)
        assertEquals(0, r.missingCount)
        assertEquals(0, r.notRecordedCount)
    }

    @Test
    fun exactPrintingAndFinishGoFirst() {
        val rows = listOf(row(1, "bolt", 1, foil = false), row(2, "bolt", 1, foil = true))
        val scans = listOf(scan("bolt", foil = true))
        val r = BoxCheck.compare(rows, scans)
        assertEquals(listOf(2L), r.found.map { it.first.rowId })
        assertEquals(listOf(1L), r.missing.map { it.first.rowId })
    }

    @Test
    fun tickedCopiesCountAsFound() {
        val rows = listOf(row(1, "bolt", 2))
        val r = BoxCheck.compare(rows, listOf(scan("bolt", 2)), ticked = mapOf(1L to 1))
        assertEquals(2, r.foundCount)
        assertEquals(1, r.notRecordedCount)
    }
}
