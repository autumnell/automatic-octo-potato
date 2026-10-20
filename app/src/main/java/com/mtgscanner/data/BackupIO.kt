package com.mtgscanner.data

import android.content.Context
import androidx.room.withTransaction
import com.mtgscanner.data.db.AppDatabase
import com.mtgscanner.data.db.Box
import com.mtgscanner.data.db.CardEvent
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.Deck
import com.mtgscanner.data.db.Goal
import com.mtgscanner.data.db.PackOpening
import com.mtgscanner.data.db.PendingScan
import com.mtgscanner.data.db.PricePoint
import com.mtgscanner.data.db.ValueSnapshot
import com.mtgscanner.data.db.WishlistItem
import com.mtgscanner.util.ImportRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate

/** One saved SharedPreferences value. [t] is its type: s, i, l, f, b or set. */
@Serializable
data class PrefValue(val t: String, val v: String? = null, val set: List<String>? = null)

@Serializable
data class Backup(
    /** Format version. 0 means the file had no marker, so it isn't one of our backups. */
    val version: Int = 0,
    val exportedAt: Long = 0L,
    val boxes: List<Box> = emptyList(),
    val cards: List<CollectionCard> = emptyList(),
    val snapshots: List<ValueSnapshot> = emptyList(),
    val decks: List<Deck> = emptyList(),
    val wishlist: List<WishlistItem> = emptyList(),
    val pending: List<PendingScan> = emptyList(),
    val priceHistory: List<PricePoint> = emptyList(),
    val packs: List<PackOpening> = emptyList(),
    val events: List<CardEvent> = emptyList(),
    val goals: List<Goal> = emptyList(),
    /** Settings that hold your organisation (deck folders, saved searches, sorting…): file name to values. Added in version 2. */
    val prefs: Map<String, Map<String, PrefValue>> = emptyMap(),
) {
    val cardCount: Int get() = cards.sumOf { it.quantity }

    companion object {
        const val CURRENT = 2
    }
}

/** A file that can't be restored; the message says why, in words for the user. */
class BadBackupException(message: String) : IOException(message)

/** Reads and writes backup files. Pure Kotlin, so it can be tested without a phone. */
object BackupCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @OptIn(ExperimentalSerializationApi::class)
    fun encode(b: Backup, out: OutputStream) = json.encodeToStream(Backup.serializer(), b, out)

    /** Reads a backup and (unless [checked] is false) checks it's safe to restore. Throws [BadBackupException] when it isn't. */
    @OptIn(ExperimentalSerializationApi::class)
    fun decode(input: InputStream, checked: Boolean = true): Backup {
        val b = try {
            json.decodeFromStream(Backup.serializer(), input)
        } catch (e: IllegalArgumentException) { // SerializationException is one
            throw BadBackupException("That isn't a backup file")
        }
        if (checked) check(b)
        return b
    }

    /** Refuses files that would wipe the app: no version marker (any other JSON), or no cards and no boxes. */
    fun check(b: Backup) {
        if (b.version <= 0) throw BadBackupException("That isn't a backup file")
        if (b.version > Backup.CURRENT) throw BadBackupException("That backup is from a newer app version")
        if (b.boxes.isEmpty() && b.cards.isEmpty()) throw BadBackupException("That backup has no cards or boxes")
    }

    /** SharedPreferences values ([android.content.SharedPreferences.getAll]) in a form JSON can hold. */
    fun prefValues(all: Map<String, *>): Map<String, PrefValue> = buildMap {
        all.forEach { (k, v) ->
            when (v) {
                is String -> put(k, PrefValue("s", v))
                is Int -> put(k, PrefValue("i", v.toString()))
                is Long -> put(k, PrefValue("l", v.toString()))
                is Float -> put(k, PrefValue("f", v.toString()))
                is Boolean -> put(k, PrefValue("b", v.toString()))
                is Set<*> -> put(k, PrefValue("set", set = v.filterIsInstance<String>()))
            }
        }
    }
}

/** Backup, restore and CSV import for [CollectionRepository]. */
internal object BackupIO {
    /** SharedPreferences files that hold the user's organisation, saved in backups. */
    val BACKED_UP_PREFS = listOf("deck_shelf", "searches", "box_covers", "sorting", "game", "scan")

    /** What the app held just before the last restore, so a restore can be undone. */
    private const val SAFETY_FILE = "before-restore.json"

    fun safetyFile(context: Context) = File(context.filesDir, SAFETY_FILE)

    private suspend fun snapshot(repo: CollectionRepository): Backup {
        prunePriceHistory(repo.db)
        val dao = repo.dao
        return Backup(
            version = Backup.CURRENT, exportedAt = System.currentTimeMillis(),
            boxes = dao.allBoxes(), cards = dao.all(), snapshots = dao.allSnapshots(),
            decks = dao.allDecks(), wishlist = dao.allWishlist(), pending = dao.allPending(),
            priceHistory = dao.allPricePoints(), packs = dao.allPacks(), events = dao.allEvents(), goals = dao.allGoals(),
            prefs = BACKED_UP_PREFS.associateWith { name ->
                BackupCodec.prefValues(repo.appContext.getSharedPreferences(name, Context.MODE_PRIVATE).all)
            }.filterValues { it.isNotEmpty() },
        )
    }

    /**
     * Writes a full backup to a temporary file first, then copies it to the stream [open] returns.
     * So a failure or a closed screen never leaves the target empty or half written.
     * Returns false when [open] gives no stream.
     */
    suspend fun export(repo: CollectionRepository, open: suspend () -> OutputStream?): Boolean = withContext(Dispatchers.IO) {
        val tmp = File.createTempFile("backup", ".json", repo.appContext.cacheDir)
        try {
            val b = snapshot(repo)
            tmp.outputStream().buffered().use { BackupCodec.encode(b, it) }
            withContext(NonCancellable) {
                val out = open() ?: return@withContext false
                out.use { o -> tmp.inputStream().use { it.copyTo(o) } }
                true
            }
        } finally {
            tmp.delete()
        }
    }

    /** Writes a backup to an app file, replacing it only once the new one is complete. */
    private suspend fun exportToFile(repo: CollectionRepository, target: File) {
        val part = File(target.parentFile, target.name + ".part")
        val b = snapshot(repo)
        part.outputStream().buffered().use { BackupCodec.encode(b, it) }
        if (!part.renameTo(target)) {
            target.delete()
            if (!part.renameTo(target)) throw IOException("Couldn't save a safety copy")
        }
    }

    /** Reads and checks a backup without changing anything. */
    suspend fun read(input: InputStream): Backup = withContext(Dispatchers.IO) {
        input.buffered().use { BackupCodec.decode(it) }
    }

    /**
     * Replaces everything with [b]. First saves what's there now to [safetyFile], so it can be undone.
     * Returns the number of cards restored.
     */
    suspend fun restore(repo: CollectionRepository, b: Backup, checked: Boolean = true): Int = withContext(Dispatchers.IO + NonCancellable) {
        if (checked) BackupCodec.check(b)
        exportToFile(repo, safetyFile(repo.appContext))
        val dao = repo.dao
        repo.db.withTransaction {
            dao.deleteAllCards(); dao.deleteAllBoxes(); dao.deleteSnapshots()
            dao.deleteDecks(); dao.deleteWishlist(); dao.deleteAllPending(); dao.deletePriceHistory(); dao.deletePacks(); dao.deleteEvents(); dao.deleteGoals()
            dao.putBoxes(b.boxes); dao.putCards(b.cards); dao.putSnapshots(b.snapshots)
            dao.putDecks(b.decks); dao.putWishlist(b.wishlist); dao.putPending(b.pending)
            dao.putPricePoints(b.priceHistory); dao.putPacks(b.packs); dao.putEvents(b.events); dao.putGoals(b.goals)
        }
        // Old backups have no settings: keep the current ones.
        if (b.prefs.isNotEmpty()) applyPrefs(repo.appContext, b.prefs)
        repo.appScope.launch { runCatching { repo.recordSnapshot() } }
        b.cardCount
    }

    /** Puts back what the app held before the last restore. Returns cards restored, or null when there's nothing to undo. */
    suspend fun undoRestore(repo: CollectionRepository): Int? = withContext(Dispatchers.IO + NonCancellable) {
        val f = safetyFile(repo.appContext)
        if (!f.exists()) return@withContext null
        // Read it fully first: restoring writes a new safety copy over this file.
        val b = f.inputStream().buffered().use { BackupCodec.decode(it, checked = false) }
        // The app may have been empty before; that's still a valid state to go back to.
        restore(repo, b, checked = false)
    }

    private fun applyPrefs(context: Context, prefs: Map<String, Map<String, PrefValue>>) {
        for (name in BACKED_UP_PREFS) {
            val values = prefs[name] ?: continue
            val e = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            values.forEach { (k, p) ->
                when (p.t) {
                    "s" -> e.putString(k, p.v)
                    "i" -> p.v?.toIntOrNull()?.let { e.putInt(k, it) }
                    "l" -> p.v?.toLongOrNull()?.let { e.putLong(k, it) }
                    "f" -> p.v?.toFloatOrNull()?.let { e.putFloat(k, it) }
                    "b" -> p.v?.toBooleanStrictOrNull()?.let { e.putBoolean(k, it) }
                    "set" -> e.putStringSet(k, p.set.orEmpty().toSet())
                }
            }
            e.commit()
        }
        SortPrefs.init(context)
        com.mtgscanner.scan.ScanPrefs.init(context)
    }

    /** Whether restoring [b] changes settings that some screens only read at start-up. */
    fun needsRestart(b: Backup) = b.prefs.keys.any { it in BACKED_UP_PREFS }

    /**
     * Keeps the last 90 days of price history in full and one point a week before that,
     * so the table (and backups) don't grow forever.
     */
    suspend fun prunePriceHistory(db: AppDatabase) = withContext(Dispatchers.IO) {
        runCatching {
            val cutoff = LocalDate.now().minusDays(90).toString()
            db.openHelper.writableDatabase.execSQL(
                "DELETE FROM price_history WHERE day < ? AND rowid NOT IN " +
                    "(SELECT MIN(rowid) FROM price_history WHERE day < ? GROUP BY scryfallId, strftime('%Y-%W', day))",
                arrayOf<Any?>(cutoff, cutoff),
            )
        }
    }

    // CSV import

    /**
     * Adds cards from another app's CSV, all in one transaction. Each card goes to [boxId], or to the
     * box whose rules fit when [boxId] is [Box.AUTO_SORT]. Cards that fit no box (or every card, when
     * [boxId] is 0) go to the box [fallbackBox] makes. Copies merged into an existing entry keep that
     * entry's condition, language and price paid.
     */
    suspend fun importRows(
        repo: CollectionRepository,
        rows: List<ImportRow>,
        boxId: Long,
        fallbackBox: (suspend () -> Long)? = null,
    ): ImportResult {
        val api = repo.api
        val found = api.identify(rows.map { identifierFor(it.name, it.setCode, it.collectorNumber, it.scryfallId) }).toMutableList()
        val retry = rows.indices.filter { found[it] == null && (rows[it].setCode != null || rows[it].scryfallId != null) }
        if (retry.isNotEmpty()) {
            val byName = api.identify(retry.map { buildJsonObject { put("name", rows[it].name) } })
            retry.zip(byName).forEach { (i, c) -> found[i] = c }
        }
        val dao = repo.dao
        var added = 0
        val notFound = mutableListOf<String>()
        // Lookups are done; from here on the import finishes even if the screen closes.
        withContext(NonCancellable) {
            repo.db.withTransaction {
                var fallbackBoxId: Long? = null
                val boxNames = HashMap<Long, String>()
                val now = System.currentTimeMillis()
                rows.zip(found).forEach { (r, c) ->
                    if (c == null) { notFound += r.name; return@forEach }
                    val chosen = when (boxId) {
                        Box.AUTO_SORT -> repo.pickBox(c, r.foil)
                        0L -> null // everything to the fallback box
                        else -> boxId
                    }
                    val target = chosen
                        ?: fallbackBoxId
                        ?: fallbackBox?.invoke()?.also { fallbackBoxId = it }
                    if (target == null) { notFound += r.name; return@forEach }
                    val row = c.toEntity(r.foil, r.quantity, now).copy(
                        boxId = target,
                        condition = r.condition ?: "NM",
                        lang = r.language ?: c.lang,
                        purchasePrice = r.purchasePrice,
                    )
                    // Same printing, finish, box, language, condition and flags: add to that stack.
                    val existing = dao.find(
                        row.scryfallId, row.foil, row.boxId, row.lang, row.condition,
                        row.signed, row.altered, row.graded, row.proxy,
                    )
                    if (existing != null) {
                        dao.update(existing.copy(quantity = existing.quantity + r.quantity, updatedAt = now))
                    } else {
                        dao.insert(row)
                    }
                    val boxName = boxNames.getOrPut(target) { dao.box(target)?.name ?: "?" }
                    dao.addEvent(CardEvent(scryfallId = c.id, name = c.name, kind = "added", detail = "×${r.quantity} → $boxName"))
                    added += r.quantity
                }
                val points = found.filterNotNull().distinctBy { it.id }.map { it.pricePoint() }
                if (points.isNotEmpty()) dao.putPricePoints(points)
            }
        }
        // One value snapshot and one role lookup for the whole import, not one per row.
        repo.appScope.launch {
            runCatching { repo.recordSnapshot() }
            runCatching { repo.fetchRoles() }
        }
        return ImportResult(added, notFound)
    }

    private fun identifierFor(name: String, set: String?, number: String?, id: String?): JsonObject = buildJsonObject {
        when {
            id != null -> put("id", id)
            set != null && number != null -> { put("set", set.lowercase()); put("collector_number", number) }
            else -> { put("name", name); if (set != null) put("set", set.lowercase()) }
        }
    }
}
