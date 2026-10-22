package com.mtgscanner.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {
    private fun row(id: String, qty: Int = 1, box: String = "Rares", updatedAt: Long = 0, cond: String = "NM") =
        LocalRow(id, mapOf("qty" to "$qty", "box" to box, "cond" to cond), base = "{\"name\":\"$id\"}", updatedAt = updatedAt)

    private fun SyncDoc.v(id: String, k: String) = records.getValue(id).values()[k]

    @Test fun `new local rows become records stamped with this phone`() {
        val d = SyncMerge.foldLocal(SyncDoc(), listOf(row("a", updatedAt = 50)), "A", now = 100, since = 0)
        val r = d.records.getValue("a")
        assertTrue(r.alive)
        assertEquals("1", r.values()["qty"])
        assertEquals(FieldValue("1", 50, "A"), r.fields["qty"])
        assertEquals(100L, d.devices["A"])
    }

    @Test fun `unchanged rows keep their stamps`() {
        val d1 = SyncMerge.foldLocal(SyncDoc(), listOf(row("a", updatedAt = 50)), "A", 100, 0)
        val d2 = SyncMerge.foldLocal(d1, listOf(row("a", updatedAt = 50)), "A", 200, 100)
        assertEquals(d1.records, d2.records)
    }

    @Test fun `only changed fields get new stamps`() {
        val d1 = SyncMerge.foldLocal(SyncDoc(), listOf(row("a", updatedAt = 50)), "A", 100, 0)
        val d2 = SyncMerge.foldLocal(d1, listOf(row("a", qty = 3, updatedAt = 150)), "A", 200, 100)
        val r = d2.records.getValue("a")
        assertEquals(FieldValue("3", 150, "A"), r.fields["qty"])
        assertEquals(50L, r.fields.getValue("box").ts)
    }

    @Test fun `edit without a fresh updatedAt is stamped just after the last sync`() {
        val d1 = SyncMerge.foldLocal(SyncDoc(), listOf(row("a", updatedAt = 50)), "A", 100, 0)
        val d2 = SyncMerge.foldLocal(d1, listOf(row("a", box = "Bulk", updatedAt = 50)), "A", 200, 100)
        assertEquals(FieldValue("Bulk", 101, "A"), d2.records.getValue("a").fields["box"])
    }

    @Test fun `new stamp always beats the old one even with a skewed clock`() {
        val doc = SyncDoc(records = mapOf("a" to SyncRecord("a", "x", mapOf("qty" to FieldValue("1", 500, "B")))))
        val d = SyncMerge.foldLocal(doc, listOf(row("a", qty = 2, updatedAt = 120)), "A", now = 200, since = 100)
        assertEquals(FieldValue("2", 501, "A"), d.records.getValue("a").fields["qty"])
    }

    @Test fun `missing rows become deletes`() {
        val d1 = SyncMerge.foldLocal(SyncDoc(), listOf(row("a"), row("b")), "A", 100, 0)
        val d2 = SyncMerge.foldLocal(d1, listOf(row("a")), "A", 200, 100)
        val b = d2.records.getValue("b")
        assertFalse(b.alive)
        assertEquals(200L, b.deletedAt)
        assertEquals("A", b.deletedBy)
    }

    @Test fun `already deleted records are not deleted again`() {
        val d1 = SyncMerge.foldLocal(SyncDoc(), listOf(row("a")), "A", 100, 0)
        val d2 = SyncMerge.foldLocal(d1, emptyList(), "A", 200, 100)
        val d3 = SyncMerge.foldLocal(d2, emptyList(), "A", 300, 200)
        assertEquals(200L, d3.records.getValue("a").deletedAt)
    }

    @Test fun `last write wins per field`() {
        val base = SyncMerge.foldLocal(SyncDoc(), listOf(row("a", updatedAt = 10)), "A", 10, 0)
        val onA = SyncMerge.foldLocal(base, listOf(row("a", qty = 2, updatedAt = 20)), "A", 30, 10)
        val onB = SyncMerge.foldLocal(base, listOf(row("a", box = "Binder", updatedAt = 25)), "B", 30, 10)
        val m = SyncMerge.merge(onA, onB)
        assertEquals("2", m.v("a", "qty"))
        assertEquals("Binder", m.v("a", "box"))
    }

    @Test fun `same field later edit wins`() {
        val base = SyncMerge.foldLocal(SyncDoc(), listOf(row("a", updatedAt = 10)), "A", 10, 0)
        val onA = SyncMerge.foldLocal(base, listOf(row("a", qty = 2, updatedAt = 20)), "A", 30, 10)
        val onB = SyncMerge.foldLocal(base, listOf(row("a", qty = 5, updatedAt = 25)), "B", 30, 10)
        assertEquals("5", SyncMerge.merge(onA, onB).v("a", "qty"))
        assertEquals("5", SyncMerge.merge(onB, onA).v("a", "qty"))
    }

    @Test fun `ties go to the higher device id`() {
        val a = SyncDoc(records = mapOf("a" to SyncRecord("a", "x", mapOf("qty" to FieldValue("1", 10, "A")))))
        val b = SyncDoc(records = mapOf("a" to SyncRecord("a", "x", mapOf("qty" to FieldValue("2", 10, "B")))))
        assertEquals("2", SyncMerge.merge(a, b).v("a", "qty"))
        assertEquals("2", SyncMerge.merge(b, a).v("a", "qty"))
    }

    @Test fun `delete wins over older edits`() {
        val base = SyncMerge.foldLocal(SyncDoc(), listOf(row("a", updatedAt = 10)), "A", 10, 0)
        val edited = SyncMerge.foldLocal(base, listOf(row("a", qty = 4, updatedAt = 20)), "B", 30, 10)
        val deleted = SyncMerge.foldLocal(base, emptyList(), "A", 40, 10)
        val m = SyncMerge.merge(edited, deleted)
        assertFalse(m.records.getValue("a").alive)
        assertFalse(SyncMerge.merge(deleted, edited).records.getValue("a").alive)
    }

    @Test fun `an edit after the delete brings the card back`() {
        val base = SyncMerge.foldLocal(SyncDoc(), listOf(row("a", updatedAt = 10)), "A", 10, 0)
        val deleted = SyncMerge.foldLocal(base, emptyList(), "A", 20, 10)
        val edited = SyncMerge.foldLocal(base, listOf(row("a", qty = 4, updatedAt = 50)), "B", 60, 10)
        val m = SyncMerge.merge(deleted, edited)
        assertTrue(m.records.getValue("a").alive)
        assertEquals("4", m.v("a", "qty"))
    }

    @Test fun `later of two deletes is kept`() {
        val r1 = SyncRecord("a", "x", deletedAt = 10, deletedBy = "A")
        val r2 = SyncRecord("a", "x", deletedAt = 20, deletedBy = "B")
        assertEquals(20L, SyncMerge.mergeRecord(r1, r2).deletedAt)
        assertEquals("B", SyncMerge.mergeRecord(r1, r2).deletedBy)
        assertEquals(SyncMerge.mergeRecord(r1, r2), SyncMerge.mergeRecord(r2, r1))
    }

    @Test fun `merge is commutative associative and idempotent`() {
        val a = SyncMerge.foldLocal(SyncDoc(), listOf(row("a", updatedAt = 5), row("b", updatedAt = 6)), "A", 10, 0)
        val b = SyncMerge.foldLocal(SyncDoc(), listOf(row("c", updatedAt = 7)), "B", 11, 0)
        val c = SyncMerge.foldLocal(a, listOf(row("a", qty = 9, updatedAt = 12)), "A", 13, 10)
        assertEquals(SyncMerge.merge(a, b), SyncMerge.merge(b, a))
        assertEquals(SyncMerge.merge(SyncMerge.merge(a, b), c), SyncMerge.merge(a, SyncMerge.merge(b, c)))
        val m = SyncMerge.merge(a, b)
        assertEquals(m, SyncMerge.merge(m, m))
        assertEquals(m, SyncMerge.merge(m, a))
    }

    @Test fun `merge keeps the base from whichever side has it`() {
        val a = SyncRecord("a", "", mapOf("qty" to FieldValue("1", 1, "A")))
        val b = SyncRecord("a", "{}", emptyMap())
        assertEquals("{}", SyncMerge.mergeRecord(a, b).base)
    }

    @Test fun `devices keep the latest sync time`() {
        val m = SyncMerge.merge(SyncDoc(devices = mapOf("A" to 5L, "B" to 9L)), SyncDoc(devices = mapOf("A" to 7L)))
        assertEquals(mapOf("A" to 7L, "B" to 9L), m.devices)
    }

    @Test fun `plan adds updates and deletes`() {
        val doc = SyncDoc(
            records = mapOf(
                "new" to SyncRecord("new", "{}", mapOf("qty" to FieldValue("2", 1, "B"))),
                "same" to SyncRecord("same", "{}", mapOf("qty" to FieldValue("1", 1, "B"))),
                "chg" to SyncRecord("chg", "{}", mapOf("qty" to FieldValue("3", 1, "B"), "box" to FieldValue("Rares", 1, "B"))),
                "gone" to SyncRecord("gone", "{}", mapOf("qty" to FieldValue("1", 1, "B")), deletedAt = 5, deletedBy = "B"),
                "goneHere" to SyncRecord("goneHere", "{}", deletedAt = 5),
                "nobase" to SyncRecord("nobase", "", mapOf("qty" to FieldValue("1", 1, "B"))),
            ),
        )
        val local = listOf(row("same"), row("chg", qty = 1), row("gone"))
        val p = SyncMerge.plan(doc, local)
        assertEquals(listOf("new"), p.inserts.map { it.id })
        assertEquals(mapOf("chg" to mapOf("qty" to "3")), p.updates)
        assertEquals(listOf("gone"), p.deletes)
        assertEquals(3, p.size)
    }

    @Test fun `plan is empty when this phone matches`() {
        val local = listOf(row("a"), row("b", qty = 2))
        val doc = SyncMerge.foldLocal(SyncDoc(), local, "A", 10, 0)
        assertTrue(SyncMerge.plan(doc, local).isEmpty)
    }

    /** Two phones syncing in turns through one file end up with the same cards. */
    @Test fun `two phones converge through the file`() {
        var file = SyncDoc()
        var stateA = SyncDoc(); var stateB = SyncDoc()
        var rowsA = listOf(row("a1", updatedAt = 1), row("a2", updatedAt = 1))
        var rowsB = listOf(row("b1", updatedAt = 2))

        fun sync(state: SyncDoc, rows: List<LocalRow>, dev: String, now: Long, since: Long): Pair<SyncDoc, List<LocalRow>> {
            val merged = SyncMerge.merge(SyncMerge.foldLocal(state, rows, dev, now, since), file)
            file = merged
            val plan = SyncMerge.plan(merged, rows)
            val byId = rows.associateBy { it.id }.toMutableMap()
            plan.deletes.forEach { byId.remove(it) }
            plan.updates.forEach { (id, f) -> byId[id] = byId.getValue(id).let { it.copy(fields = it.fields + f) } }
            plan.inserts.forEach { r -> byId[r.id] = LocalRow(r.id, r.values(), r.base, now) }
            return merged to byId.values.sortedBy { it.id }
        }

        sync(stateA, rowsA, "A", 10, 0).let { stateA = it.first; rowsA = it.second }
        sync(stateB, rowsB, "B", 11, 0).let { stateB = it.first; rowsB = it.second }
        assertEquals(listOf("a1", "a2", "b1"), rowsB.map { it.id })
        // A deletes a2 and B changes a1 at the same time.
        rowsA = rowsA.filter { it.id != "a2" }
        rowsB = rowsB.map { if (it.id == "a1") it.copy(fields = it.fields + ("qty" to "7"), updatedAt = 15) else it }
        sync(stateA, rowsA, "A", 20, 10).let { stateA = it.first; rowsA = it.second }
        sync(stateB, rowsB, "B", 21, 11).let { stateB = it.first; rowsB = it.second }
        sync(stateA, rowsA, "A", 30, 20).let { stateA = it.first; rowsA = it.second }
        assertEquals(rowsA.map { it.id to it.fields }, rowsB.map { it.id to it.fields })
        assertEquals(listOf("a1", "b1"), rowsA.map { it.id })
        assertEquals("7", rowsA.first().fields["qty"])
    }

    /** A phone that missed a write (the other phone overwrote the file) still gets its change in next time. */
    @Test fun `a lost write comes back on the next sync`() {
        val a = SyncMerge.foldLocal(SyncDoc(), listOf(row("a", updatedAt = 1)), "A", 10, 0)
        val b = SyncMerge.foldLocal(SyncDoc(), listOf(row("b", updatedAt = 1)), "B", 10, 0)
        // Both write at once; B's write lands last, so the file only has b.
        val file = b
        val again = SyncMerge.merge(SyncMerge.foldLocal(a, listOf(row("a", updatedAt = 1)), "A", 20, 10), file)
        assertEquals(setOf("a", "b"), again.records.keys)
    }

    @Test fun `prune drops old deletes only after every phone has synced`() {
        val doc = SyncDoc(
            records = mapOf(
                "old" to SyncRecord("old", "{}", deletedAt = 5),
                "new" to SyncRecord("new", "{}", deletedAt = 50),
                "live" to SyncRecord("live", "{}", mapOf("qty" to FieldValue("1", 1, "A"))),
            ),
            devices = mapOf("A" to 100L, "B" to 100L),
        )
        assertEquals(setOf("new", "live"), SyncMerge.prune(doc, 20).records.keys)
        val lagging = doc.copy(devices = mapOf("A" to 100L, "B" to 10L))
        assertEquals(doc.records.keys, SyncMerge.prune(lagging, 20).records.keys)
    }

    @Test fun `alive and lastChange`() {
        val r = SyncRecord("a", "", mapOf("qty" to FieldValue("1", 30, "A")), deletedAt = 20)
        assertTrue(r.alive)
        assertEquals(30L, r.lastChange)
        assertNull(SyncRecord("b").deletedAt)
        assertTrue(SyncRecord("b").alive)
    }
}
