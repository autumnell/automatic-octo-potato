package com.mtgscanner.data.sync

import com.mtgscanner.data.db.CollectionCard
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The fields of a collection row that are shared between phones, and how they turn into text and back. */
object SyncFields {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    const val BOX = "box"

    /** Field values of [c]. The box is sent by name, since box ids differ between phones. */
    fun of(c: CollectionCard, boxName: String): Map<String, String> = mapOf(
        "qty" to c.quantity.toString(),
        BOX to boxName,
        "foil" to c.foil.toString(),
        "lang" to c.lang,
        "cond" to c.condition,
        "notes" to c.notes,
        "tags" to c.userTags,
        "paid" to num(c.purchasePrice),
        "from" to c.acquiredFrom,
        "sale" to c.forSale.toString(),
        "ask" to num(c.askPrice),
        "pin" to c.pinned.toString(),
        "slot" to c.binderSlot.toString(),
        "signed" to c.signed.toString(),
        "altered" to c.altered.toString(),
        "graded" to c.graded,
        "proxy" to c.proxy.toString(),
    )

    /** [c] with [f] applied. The box isn't applied here; the caller looks it up by name. */
    fun apply(c: CollectionCard, f: Map<String, String>): CollectionCard {
        var r = c
        f["qty"]?.toIntOrNull()?.let { r = r.copy(quantity = it.coerceAtLeast(1)) }
        f["foil"]?.let { r = r.copy(foil = it == "true") }
        f["lang"]?.let { if (it.isNotBlank()) r = r.copy(lang = it) }
        f["cond"]?.let { if (it.isNotBlank()) r = r.copy(condition = it) }
        f["notes"]?.let { r = r.copy(notes = it) }
        f["tags"]?.let { r = r.copy(userTags = it) }
        f["paid"]?.let { r = r.copy(purchasePrice = it.toDoubleOrNull()) }
        f["from"]?.let { r = r.copy(acquiredFrom = it) }
        f["sale"]?.let { r = r.copy(forSale = it == "true") }
        f["ask"]?.let { r = r.copy(askPrice = it.toDoubleOrNull()) }
        f["pin"]?.let { r = r.copy(pinned = it == "true") }
        f["slot"]?.toIntOrNull()?.let { r = r.copy(binderSlot = it) }
        f["signed"]?.let { r = r.copy(signed = it == "true") }
        f["altered"]?.let { r = r.copy(altered = it == "true") }
        f["graded"]?.let { r = r.copy(graded = it) }
        f["proxy"]?.let { r = r.copy(proxy = it == "true") }
        return r
    }

    private fun num(d: Double?): String = d?.toString() ?: ""

    /** The printing part of a row, for adding it on the other phone. Local-only bits are left out. */
    fun base(c: CollectionCard): String = json.encodeToString(CollectionCard.serializer(), c.copy(id = 0, photo = "", boxId = 0))

    fun fromBase(base: String): CollectionCard? = runCatching { json.decodeFromString(CollectionCard.serializer(), base) }.getOrNull()
}

/** This phone's row id ↔ the shared id. The printing id guards against a row id being reused for another card. */
@Serializable
data class IdEntry(val id: String, val scryfallId: String)

@Serializable
data class IdMap(val rows: Map<Long, IdEntry> = emptyMap()) {
    /** The shared id of row [rowId] holding [scryfallId], or null if it has none (or the row id was reused). */
    fun idOf(rowId: Long, scryfallId: String): String? = rows[rowId]?.takeIf { it.scryfallId == scryfallId }?.id

    /**
     * Ids for all of [rows] (rowId to scryfallId): existing ones kept, new ones made with [newId].
     * Rows that no longer exist are dropped.
     */
    fun assign(rows: List<Pair<Long, String>>, newId: () -> String): IdMap {
        val used = HashSet<String>()
        val out = LinkedHashMap<Long, IdEntry>()
        for ((rowId, sid) in rows) {
            val known = idOf(rowId, sid)?.takeIf { it !in used }
            val id = known ?: newId()
            used += id
            out[rowId] = IdEntry(id, sid)
        }
        return IdMap(out)
    }

    fun with(rowId: Long, entry: IdEntry): IdMap = IdMap(rows + (rowId to entry))
    fun without(rowId: Long): IdMap = IdMap(rows - rowId)
    fun rowOf(id: String): Long? = rows.entries.firstOrNull { it.value.id == id }?.key
}
