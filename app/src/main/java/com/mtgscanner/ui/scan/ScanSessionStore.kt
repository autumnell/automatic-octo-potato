package com.mtgscanner.ui.scan

import com.mtgscanner.data.ScryfallCard
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * What survives the app closing mid-pile: the scan list, sorting session, set-aside cards, pack and box check.
 *
 * On disk each line is written once, in [entries], and the scan list, sort and pack name theirs by key
 * ([sessionKeys], [sortKeys], [packKeys]); see [compact] and [expanded]. Files from before that keep the lines
 * inside [session], [sort] and [pack], and still load.
 */
@Serializable
data class SavedScanSession(
    val session: List<ScanEntry> = emptyList(),
    val sort: SortSession? = null,
    val review: List<ReviewItem> = emptyList(),
    val pack: PackDraft? = null,
    val boxCheck: BoxCheckRun? = null,
    /** Every line once, with a trimmed card. Empty in memory. */
    val entries: List<ScanEntry> = emptyList(),
    val sessionKeys: List<Long>? = null,
    val sortKeys: List<Long>? = null,
    val packKeys: List<Long>? = null,
) {
    val isEmpty: Boolean
        get() = session.isEmpty() && sort == null && review.isEmpty() && pack == null && boxCheck == null && entries.isEmpty()

    /** The form written to disk: each line once, by key. */
    fun compact(): SavedScanSession {
        if (entries.isNotEmpty()) return this
        val pool = LinkedHashMap<Long, ScanEntry>()
        (session + sort?.entries.orEmpty() + pack?.entries.orEmpty()).forEach { e ->
            if (e.key !in pool) pool[e.key] = e.copy(card = e.card.forSession())
        }
        return SavedScanSession(
            review = review,
            boxCheck = boxCheck,
            sort = sort?.copy(entries = emptyList()),
            pack = pack?.copy(entries = emptyList()),
            entries = pool.values.toList(),
            sessionKeys = session.map { it.key },
            sortKeys = sort?.entries?.map { it.key },
            packKeys = pack?.entries?.map { it.key },
        )
    }

    /** The in-memory form: the lists hold their lines again. Old files are already in it. */
    fun expanded(): SavedScanSession {
        if (entries.isEmpty()) return this
        val byKey = entries.associateBy { it.key }
        fun lines(keys: List<Long>?, old: List<ScanEntry>) = keys?.mapNotNull { byKey[it] } ?: old
        return SavedScanSession(
            session = lines(sessionKeys, session),
            sort = sort?.let { it.copy(entries = lines(sortKeys, it.entries)) },
            review = review,
            pack = pack?.let { it.copy(entries = lines(packKeys, it.entries)) },
            boxCheck = boxCheck,
        )
    }

    /** Same lists as [other], by reference: a cheap check that nothing worth saving changed. */
    fun sameAs(other: SavedScanSession): Boolean =
        session === other.session && sort === other.sort && review === other.review && pack === other.pack &&
            boxCheck === other.boxCheck

    companion object {
        fun of(s: ScanUiState) = SavedScanSession(s.session, s.sort, s.review, s.pack, s.boxCheck)
    }
}

/**
 * A scan list line's card without what the scanner never reads back: store and site links, flavor text, and the
 * formats it isn't legal in. Kept: everything a copy added again from the line needs (oracle text, keywords, the
 * legal and restricted formats used for tags).
 */
internal fun ScryfallCard.forSession(): ScryfallCard = copy(
    legalities = legalities.filterValues { it == "legal" || it == "restricted" },
    relatedUris = emptyMap(),
    purchaseUris = emptyMap(),
    flavorText = null,
    cardFaces = cardFaces?.map { it.copy(flavorText = null) },
)

/** Keeps [SavedScanSession] in one JSON file in filesDir. Written through a temp file so a crash can't half-write it. */
class ScanSessionStore(dir: File) {
    private val file = File(dir, "scan-session.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    fun load(): SavedScanSession? = runCatching {
        if (!file.exists()) null else json.decodeFromString(SavedScanSession.serializer(), file.readText()).expanded()
    }.getOrNull()

    fun save(s: SavedScanSession) {
        runCatching {
            if (s.isEmpty) { file.delete(); return }
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(SavedScanSession.serializer(), s.compact()))
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }
    }
}
