package com.mtgscanner.data.sync

import kotlinx.serialization.Serializable

/*
 * Shared collection between two phones, with no server: both phones point at one file (say in a shared
 * Google Drive folder). The file holds every card's fields, each stamped with when and on which phone it
 * last changed. Syncing folds this phone's changes in, merges the file, writes it back and applies the
 * result here. Merging is order-free and repeatable, so two phones writing at once still end up the same
 * after their next syncs.
 *
 * Rules: last write wins per field; a delete wins over edits made before it; an edit made after a delete
 * brings the card back.
 */

/** One field's value and who wrote it when. */
@Serializable
data class FieldValue(val v: String, val ts: Long, val dev: String)

/** One collection row, keyed by an id that's the same on every phone. */
@Serializable
data class SyncRecord(
    val id: String,
    /** The printing (name, set, prices, image…) as JSON, for adding the row on a phone that doesn't have it. */
    val base: String = "",
    val fields: Map<String, FieldValue> = emptyMap(),
    /** When it was deleted, or null. */
    val deletedAt: Long? = null,
    val deletedBy: String = "",
) {
    /** Still in the collection: never deleted, or edited after the delete. */
    val alive: Boolean get() = deletedAt == null || fields.values.any { it.ts > deletedAt }

    fun values(): Map<String, String> = fields.mapValues { it.value.v }

    /** Newest change of any kind. */
    val lastChange: Long get() = maxOf(fields.values.maxOfOrNull { it.ts } ?: 0L, deletedAt ?: 0L)
}

@Serializable
data class SyncDoc(
    val version: Int = 1,
    val records: Map<String, SyncRecord> = emptyMap(),
    /** Each phone's last sync time, by device id. */
    val devices: Map<String, Long> = emptyMap(),
) {
    val aliveCount: Int get() = records.values.count { it.alive }
}

/** A row as it is on this phone right now: its stable id, field values and printing JSON. */
data class LocalRow(
    val id: String,
    val fields: Map<String, String>,
    val base: String,
    /** When the row last changed here (0 if unknown). */
    val updatedAt: Long = 0,
)

/** What to change on this phone so it matches the merged file. */
data class ApplyPlan(
    /** Cards to add: id → record. */
    val inserts: List<SyncRecord>,
    /** Rows to change: id → only the fields that differ. */
    val updates: Map<String, Map<String, String>>,
    /** Rows to delete, by id. */
    val deletes: List<String>,
) {
    val isEmpty: Boolean get() = inserts.isEmpty() && updates.isEmpty() && deletes.isEmpty()
    val size: Int get() = inserts.size + updates.size + deletes.size
}

object SyncMerge {
    /** Whether [a] beats [b]: later time wins; ties go to the higher device id, then the higher value. */
    fun wins(a: FieldValue, b: FieldValue): Boolean = when {
        a.ts != b.ts -> a.ts > b.ts
        a.dev != b.dev -> a.dev > b.dev
        else -> a.v > b.v
    }

    fun mergeRecord(a: SyncRecord, b: SyncRecord): SyncRecord {
        val fields = HashMap(a.fields)
        b.fields.forEach { (k, v) ->
            val mine = fields[k]
            if (mine == null || wins(v, mine)) fields[k] = v
        }
        val (deletedAt, deletedBy) = when {
            a.deletedAt == null -> b.deletedAt to b.deletedBy
            b.deletedAt == null -> a.deletedAt to a.deletedBy
            a.deletedAt != b.deletedAt -> if (a.deletedAt > b.deletedAt) a.deletedAt to a.deletedBy else b.deletedAt to b.deletedBy
            else -> a.deletedAt to maxOf(a.deletedBy, b.deletedBy)
        }
        return SyncRecord(
            id = a.id,
            base = a.base.ifEmpty { b.base },
            fields = fields,
            deletedAt = deletedAt,
            deletedBy = deletedBy,
        )
    }

    /** Both docs combined. The same result whatever the order, and merging again changes nothing. */
    fun merge(a: SyncDoc, b: SyncDoc): SyncDoc {
        val records = HashMap(a.records)
        b.records.forEach { (id, r) -> records[id] = records[id]?.let { mergeRecord(it, r) } ?: r }
        val devices = HashMap(a.devices)
        b.devices.forEach { (d, t) -> devices[d] = maxOf(devices[d] ?: 0L, t) }
        return SyncDoc(version = maxOf(a.version, b.version), records = records, devices = devices)
    }

    /**
     * [doc] (this phone's last merged state) with this phone's changes since then folded in:
     * new rows, fields that differ, and rows that are gone. Edits are stamped with the row's own change time
     * when it's after [since] (the last sync), else just after [since]; deletes are stamped [now].
     */
    fun foldLocal(doc: SyncDoc, local: List<LocalRow>, device: String, now: Long, since: Long): SyncDoc {
        val records = HashMap(doc.records)
        val seen = HashSet<String>()
        for (row in local) {
            seen += row.id
            val ts = editTime(row.updatedAt, since, now)
            val old = records[row.id]
            if (old == null) {
                records[row.id] = SyncRecord(
                    id = row.id, base = row.base,
                    fields = row.fields.mapValues { FieldValue(it.value, ts, device) },
                )
                continue
            }
            val current = old.values()
            val changed = row.fields.filter { (k, v) -> current[k] != v }
            if (changed.isEmpty() && old.base.isNotEmpty()) continue
            val fields = HashMap(old.fields)
            // A new stamp must beat the old one, even when clocks disagree a little.
            changed.forEach { (k, v) -> fields[k] = FieldValue(v, maxOf(ts, (old.fields[k]?.ts ?: 0L) + 1), device) }
            records[row.id] = old.copy(base = old.base.ifEmpty { row.base }, fields = fields)
        }
        for ((id, r) in doc.records) {
            if (id !in seen && r.alive) {
                records[id] = r.copy(deletedAt = maxOf(now, r.lastChange + 1), deletedBy = device)
            }
        }
        val devices = HashMap(doc.devices)
        devices[device] = now
        return doc.copy(records = records, devices = devices)
    }

    private fun editTime(updatedAt: Long, since: Long, now: Long): Long =
        (if (updatedAt > since) updatedAt else since + 1).coerceAtMost(now).coerceAtLeast(1)

    /** What this phone must change so its rows match [doc]. */
    fun plan(doc: SyncDoc, local: List<LocalRow>): ApplyPlan {
        val byId = local.associateBy { it.id }
        val inserts = mutableListOf<SyncRecord>()
        val updates = LinkedHashMap<String, Map<String, String>>()
        val deletes = mutableListOf<String>()
        for (r in doc.records.values.sortedBy { it.id }) {
            val row = byId[r.id]
            when {
                !r.alive && row != null -> deletes += r.id
                !r.alive -> Unit
                row == null -> if (r.base.isNotEmpty()) inserts += r
                else -> {
                    val diff = r.values().filter { (k, v) -> row.fields[k] != v }
                    if (diff.isNotEmpty()) updates[r.id] = diff
                }
            }
        }
        return ApplyPlan(inserts, updates, deletes)
    }

    /** Forgets deletes older than [before], once every phone has synced since. Keeps the file from growing forever. */
    fun prune(doc: SyncDoc, before: Long): SyncDoc {
        if (doc.devices.isEmpty() || doc.devices.values.any { it < before }) return doc
        return doc.copy(records = doc.records.filterValues { it.alive || (it.deletedAt ?: 0L) >= before })
    }
}
