package com.mtgscanner.data

import android.content.Context
import androidx.room.withTransaction
import com.mtgscanner.data.db.AppDatabase
import com.mtgscanner.data.db.Box
import com.mtgscanner.data.db.CardEvent
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.Deck
import com.mtgscanner.data.db.Goal
import com.mtgscanner.data.db.Loan
import com.mtgscanner.data.db.PackOpening
import com.mtgscanner.data.db.PendingScan
import com.mtgscanner.data.db.PricePoint
import com.mtgscanner.data.db.ValueSnapshot
import com.mtgscanner.data.db.WishlistItem
import com.mtgscanner.data.db.placeName
import com.mtgscanner.util.ImportRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
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
    /** Copies lent out (DB v12). Older backups have none. */
    val loans: List<Loan> = emptyList(),
    /** Settings that hold your organisation (deck folders, saved searches, sorting…): file name to values. Added in version 2. */
    val prefs: Map<String, Map<String, PrefValue>> = emptyMap(),
    /** Where a zip backup's price history and photos were unpacked when read; not part of the file. */
    @kotlinx.serialization.Transient val staged: String? = null,
) {
    val cardCount: Int get() = cards.sumOf { it.quantity }

    companion object {
        const val CURRENT = 3
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
    /** SharedPreferences files that hold the user's organisation and options, saved in backups. */
    val BACKED_UP_PREFS = listOf(
        "deck_shelf", "searches", "box_covers", "sorting", "game", "scan", "app", "boxes", "box_checked", "deck_links", "deck_status",
        // Game stats, theme and fonts, set-completion settings and the scan bar.
        "game_records", "appearance", "sets", "scanBar",
    )

    /**
     * Files keyed by box or deck id: the shelf and box-check start dates ([BoxPrefs]), when boxes were last checked
     * ([LastChecked]) and deck links. A restore keeps the backup's ids, so these must come from the backup too.
     * They're saved even when empty, so a backup without them is from an older version.
     */
    private val ID_PREFS = listOf("boxes", "box_checked", "deck_links", "deck_status")

    /** What the app held just before the last restore, so a restore can be undone. */
    private const val SAFETY_FILE = "before-restore.json"

    fun safetyFile(context: Context) = File(context.filesDir, SAFETY_FILE)

    /** A backup the app saved by itself before a big change. */
    class AutoBackup(val file: File, val at: Long, val reason: String)

    private const val AUTO_DIR = "auto-backups"
    /** How many automatic backups are kept; older ones are deleted. */
    private const val AUTO_KEEP = 3
    /** Two big changes this close together share one backup. */
    private const val AUTO_GAP_MS = 10 * 60_000L

    private fun autoDir(context: Context) = File(context.filesDir, AUTO_DIR)

    /** The automatic backups on the phone, newest first. */
    fun autoBackups(context: Context): List<AutoBackup> =
        (autoDir(context).listFiles() ?: emptyArray()).filter { it.isFile && it.name.endsWith(".zip") }.mapNotNull { f ->
            val name = f.name.removeSuffix(".zip")
            val at = name.substringBefore('_').toLongOrNull() ?: return@mapNotNull null
            AutoBackup(f, at, name.substringAfter('_', "").replace('-', ' '))
        }.sortedByDescending { it.at }

    /**
     * Saves a quick backup before a big change ([reason], like "delete box"), so it can be brought back even where Undo
     * can't reach. Keeps the newest few; skipped when one was saved in the last few minutes. Never throws.
     */
    suspend fun autoBackup(repo: CollectionRepository, reason: String) = withContext(Dispatchers.IO + NonCancellable) {
        runCatching {
            val ctx = repo.appContext
            val now = System.currentTimeMillis()
            val existing = autoBackups(ctx)
            if (existing.firstOrNull()?.let { now - it.at < AUTO_GAP_MS } == true) return@runCatching
            if (repo.dao.all().isEmpty()) return@runCatching
            val dir = autoDir(ctx).apply { mkdirs() }
            val slug = reason.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
            exportToFile(repo, File(dir, "${now}_$slug.zip"))
            autoBackups(ctx).drop(AUTO_KEEP).forEach { it.file.delete() }
        }
    }

    /** Everything but price history, which is streamed straight from the table (see [writeArchive]). */
    private suspend fun snapshot(repo: CollectionRepository): Backup {
        prunePriceHistory(repo.db)
        val dao = repo.dao
        return Backup(
            version = Backup.CURRENT, exportedAt = System.currentTimeMillis(),
            boxes = dao.allPlaces(), cards = dao.all(), snapshots = dao.allSnapshots(),
            decks = dao.allDecks(), wishlist = dao.allWishlist(), pending = dao.allPending(),
            packs = dao.allPacks(), events = dao.allEvents(), goals = dao.allGoals(), loans = dao.allLoans(),
            prefs = BACKED_UP_PREFS.associateWith { name ->
                BackupCodec.prefValues(repo.appContext.getSharedPreferences(name, Context.MODE_PRIVATE).all)
            }.filter { (name, values) -> values.isNotEmpty() || name in ID_PREFS },
        )
    }

    /** Writes a full backup (zip: data, price history row by row, card photos) to [out], and closes it. Returns the card count. */
    private suspend fun writeArchive(repo: CollectionRepository, out: OutputStream): Int {
        val b = snapshot(repo)
        val photoDir = CardPhotos.folder(repo.appContext)
        val photos = b.cards.mapNotNull { c -> c.photo.takeIf { it.isNotBlank() }?.let { File(photoDir, File(it).name) } }
        BackupArchive.write(out, b, photos) { emit ->
            repo.db.openHelper.readableDatabase.query("SELECT scryfallId, day, usd, usdFoil FROM price_history").use { c ->
                while (c.moveToNext()) {
                    emit(
                        PricePoint(
                            c.getString(0), c.getString(1),
                            if (c.isNull(2)) null else c.getDouble(2),
                            if (c.isNull(3)) null else c.getDouble(3),
                        ),
                    )
                }
            }
        }
        return b.cardCount
    }

    /**
     * Writes a full backup to a temporary file first, then copies it to the stream [open] returns.
     * So a failure or a closed screen never leaves the target empty or half written.
     * Returns false when [open] gives no stream.
     */
    suspend fun export(repo: CollectionRepository, open: suspend () -> OutputStream?): Boolean =
        exportCounted(repo) { open() } != null

    /**
     * Like [export], but [open] is told how many cards the finished backup holds (it can refuse with null).
     * Returns the cards saved, or null when [open] gives no stream.
     */
    suspend fun exportCounted(repo: CollectionRepository, open: suspend (cards: Int) -> OutputStream?): Int? = withContext(Dispatchers.IO) {
        val tmp = File.createTempFile("backup", ".zip", repo.appContext.cacheDir)
        try {
            val count = writeArchive(repo, tmp.outputStream())
            withContext(NonCancellable) {
                val out = open(count) ?: return@withContext null
                out.use { o -> tmp.inputStream().use { it.copyTo(o) } }
                count
            }
        } finally {
            tmp.delete()
        }
    }

    /** Writes a backup to an app file, replacing it only once the new one is complete. */
    private suspend fun exportToFile(repo: CollectionRepository, target: File) {
        val part = File(target.parentFile, target.name + ".part")
        writeArchive(repo, part.outputStream())
        if (!part.renameTo(target)) {
            target.delete()
            if (!part.renameTo(target)) throw IOException("Couldn't save a safety copy")
        }
    }

    /** Reads and checks a backup without changing anything. A zip's photos and prices wait in the cache until restored. */
    suspend fun read(input: InputStream, context: Context? = null): Backup = withContext(Dispatchers.IO) {
        val stage = File(context?.cacheDir ?: File(System.getProperty("java.io.tmpdir") ?: "."), "restore-staging")
        input.use { BackupArchive.read(it, stage) }
    }

    /**
     * Replaces everything with [b]. First saves what's there now to [safetyFile], so it can be undone.
     * Returns the number of cards restored.
     */
    suspend fun restore(repo: CollectionRepository, b: Backup, checked: Boolean = true): Int = withContext(Dispatchers.IO + NonCancellable) {
        if (checked) BackupCodec.check(b)
        exportToFile(repo, safetyFile(repo.appContext))
        // Kept with the automatic backups too, since the next restore writes over the safety copy.
        runCatching {
            val dir = autoDir(repo.appContext).apply { mkdirs() }
            safetyFile(repo.appContext).copyTo(File(dir, "${System.currentTimeMillis()}_restore.zip"), overwrite = true)
            autoBackups(repo.appContext).drop(AUTO_KEEP).forEach { it.file.delete() }
        }
        // Photos first, so the restored cards can point at this phone's copies.
        val photoDir = CardPhotos.folder(repo.appContext)
        BackupArchive.stagedPhotos(b).forEach { f -> runCatching { f.copyTo(File(photoDir, f.name), overwrite = true) } }
        // Old JustTCG condition prices no longer count; an older backup's are dropped.
        val cards = b.cards.map { it.copy(conditionPrice = null, conditionPriceAt = 0) }.map { c ->
            if (c.photo.isBlank()) c
            else File(photoDir, File(c.photo).name).takeIf { it.isFile }?.let { c.copy(photo = it.absolutePath) } ?: c
        }
        val dao = repo.dao
        repo.db.withTransaction {
            dao.deleteAllCards(); dao.deleteAllBoxes(); dao.deleteSnapshots()
            dao.deleteDecks(); dao.deleteWishlist(); dao.deleteAllPending(); dao.deletePriceHistory(); dao.deletePacks(); dao.deleteEvents(); dao.deleteGoals(); dao.deleteLoans()
            dao.putBoxes(b.boxes); dao.putCards(cards); dao.putSnapshots(b.snapshots)
            dao.putDecks(b.decks); dao.putWishlist(b.wishlist); dao.putPending(b.pending)
            b.priceHistory.chunked(2000).forEach { dao.putPricePoints(it) }
            BackupArchive.forEachPriceChunk(b) { dao.putPricePoints(it) }
            dao.putPacks(b.packs); dao.putEvents(b.events); dao.putGoals(b.goals); dao.putLoans(b.loans)
        }
        b.staged?.let { runCatching { File(it).deleteRecursively() } }
        // Old backups have no settings: keep the current ones.
        if (b.prefs.isNotEmpty()) applyPrefs(repo.appContext, b.prefs)
        forgetIdPrefs(repo.appContext, b.prefs)
        repo.appScope.launch { runCatching { repo.recordSnapshot() } }
        b.cardCount
    }

    /** Puts back what the app held before the last restore. Returns cards restored, or null when there's nothing to undo. */
    suspend fun undoRestore(repo: CollectionRepository): Int? = withContext(Dispatchers.IO + NonCancellable) {
        val f = safetyFile(repo.appContext)
        if (!f.exists()) return@withContext null
        // Read it fully first: restoring writes a new safety copy over this file.
        val stage = File(repo.appContext.cacheDir, "undo-staging")
        val b = f.inputStream().use { BackupArchive.read(it, stage, checked = false) }
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
        com.mtgscanner.ui.theme.AppSettings.init(context)
        com.mtgscanner.work.CardAlertStore.reload(context)
        com.mtgscanner.ui.decks.DeckStatusStore.reload(context)
    }

    /**
     * A backup from before box and deck prefs were saved: this phone's ones name its own ids, which now belong to the
     * backup's boxes and decks. They're dropped (the shelf keeps its size) rather than point at the wrong box or deck.
     * Then the box prefs are read again either way.
     */
    private fun forgetIdPrefs(context: Context, prefs: Map<String, Map<String, PrefValue>>) {
        for (name in ID_PREFS) {
            if (name in prefs) continue
            val p = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            when (name) {
                "boxes" -> {
                    val shelf = ShelfLayout.decode(p.getString("shelf", null))
                    p.edit().putString("shelf", ShelfLayout(shelf.rows, shelf.cols).encode()).remove("checks").commit()
                }
                "box_checked" -> p.edit().remove("done").commit()
                else -> p.edit().clear().commit()
            }
        }
        BoxPrefs.reload(context)
        LastChecked.reload(context)
        com.mtgscanner.ui.decks.DeckStatusStore.reload(context)
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
    ): ImportResult = applyPlan(repo, plan(repo, rows, lookUp(repo, rows), boxId), fallbackBox)

    /**
     * Looks every row up on Scryfall (exact matches only). A row that names a set gets that set's printing
     * (and that collector number when given; with no number, the set's normal printing); only when there's no such printing does it fall back to the
     * card in that set, then to any printing ([ImportPlan.otherPrinting] lists those). Throws IOException when offline.
     */
    suspend fun lookUp(repo: CollectionRepository, given: List<ImportRow>): List<ScryfallCard?> {
        val api = repo.api
        val rows = withSetCodes(repo, given)
        val wants = rows.map { PrintingLookup.Wanted(it.name, it.setCode, it.collectorNumber, it.scryfallId) }
        val found = PrintingLookup.resolve(wants) { ids -> api.identify(ids.map(PrintingLookup::json)) }
        // A set with no number gets that set's normal printing, not a borderless or showcase one.
        return api.preferRegular(wants, found)
    }

    /** Fills in set codes for rows that only have a full set name (Deckbox, Cardmarket), from Scryfall's set list. */
    private suspend fun withSetCodes(repo: CollectionRepository, rows: List<ImportRow>): List<ImportRow> {
        if (rows.none { it.setCode == null && !it.setName.isNullOrBlank() }) return rows
        val sets = runCatching { repo.sets() }.getOrNull() ?: return rows
        val byName = SetNames.index(sets.map { it.name to it.code })
        return rows.map { r ->
            if (r.setCode != null || r.setName.isNullOrBlank()) r
            else SetNames.codeFor(r.setName, byName)?.let { r.copy(setCode = it) } ?: r
        }
    }

    /**
     * Looks one row up again, more loosely: exact first, then Scryfall's fuzzy name match (typos,
     * partial names), with its set and then without. Null when still not found. Throws IOException when offline.
     */
    suspend fun lookUpLoosely(repo: CollectionRepository, given: ImportRow): ScryfallCard? {
        val row = withSetCodes(repo, listOf(given)).first()
        lookUp(repo, listOf(row)).firstOrNull()?.let { return it }
        val name = row.name.trim().takeIf { it.isNotEmpty() } ?: return null
        val set = PrintingLookup.setCode(row.setCode)
        return (if (set != null) repo.api.named(name, set) else null) ?: repo.api.named(name)
    }

    /** Works out which box each found card goes to, without changing anything. Same rules as adding them. */
    suspend fun plan(repo: CollectionRepository, rows: List<ImportRow>, found: List<ScryfallCard?>, choice: Long): ImportPlan {
        val boxes = repo.boxInfos()
        val policy = SortPrefs.policy
        val items = rows.zip(found).map { (r, c) -> c?.let { ImportPlanner.Item(it.facts(r.foil), it.oracleId, r.quantity) } }
        // A box deleted since it was picked: send the cards to a new box instead.
        val pick = if (choice == Box.AUTO_SORT || choice == ImportPlan.NEW_BOX || boxes.any { it.id == choice }) choice else ImportPlan.NEW_BOX
        // Copies already kept in main boxes, for the playset limit. Read up front: the planner doesn't suspend.
        val owned = HashMap<String, Int>()
        if (pick == Box.AUTO_SORT && policy.playsetLimit > 0) {
            val notMain = boxes.filterNot { Sorting.isMainBox(it, policy) }.map { it.id }.ifEmpty { listOf(Long.MIN_VALUE) }
            items.mapNotNull { it?.oracleId }.distinct().forEach { owned[it] = repo.dao.ownedInBoxesOutside(it, notMain) }
        }
        val targets = ImportPlanner.targets(items, pick, boxes, policy) { owned[it] ?: 0 }
        return ImportPlan(rows, found, targets, choice)
    }

    /**
     * Adds a planned import in one transaction. Rows marked [ImportPlan.NEW_BOX], or whose box was deleted
     * since, go to the box [fallbackBox] makes (made once, only when needed). Copies merged into an existing
     * entry keep that entry's condition, language and price paid.
     */
    suspend fun applyPlan(repo: CollectionRepository, plan: ImportPlan, fallbackBox: (suspend () -> Long)? = null): ImportResult {
        val dao = repo.dao
        var added = 0
        val notFound = mutableListOf<String>()
        // Copies added per row id, and the box made for the import, so Undo takes off exactly this.
        val addedTo = LinkedHashMap<Long, Int>()
        var fallbackBoxId: Long? = null
        var madeBox: Long? = null
        // Lookups are done; from here on the import finishes even if the screen closes.
        withContext(NonCancellable) {
            repo.db.withTransaction {
                // Decks' card rows too: a deck's "Add to collection" plans its cards into the deck.
                val boxNames = dao.allPlaces().associate { it.id to it.placeName() }.toMutableMap()
                val now = System.currentTimeMillis()
                plan.rows.forEachIndexed { i, r ->
                    val c = plan.found.getOrNull(i)
                    if (c == null) { notFound += r.name; return@forEachIndexed }
                    val chosen = plan.targets.getOrNull(i)?.takeIf { it != ImportPlan.NEW_BOX && it in boxNames }
                    val target = chosen
                        ?: fallbackBoxId
                        ?: fallbackBox?.invoke()?.also { id ->
                            fallbackBoxId = id
                            // A box that wasn't there before was made for this import.
                            if (id !in boxNames) madeBox = id
                            boxNames[id] = dao.box(id)?.name ?: "?"
                        }
                    if (target == null) { notFound += r.name; return@forEachIndexed }
                    val row = c.toEntity(r.foil, r.quantity, now).copy(
                        boxId = target,
                        condition = r.condition ?: "NM",
                        lang = r.language ?: c.lang,
                        purchasePrice = r.purchasePrice,
                        signed = r.signed,
                        altered = r.altered,
                        proxy = r.proxy,
                    )
                    // Same printing, finish, box, language, condition and flags: add to that stack.
                    val existing = dao.find(
                        row.scryfallId, row.foil, row.boxId, row.lang, row.condition,
                        row.signed, row.altered, row.graded, row.proxy,
                    )
                    val rowId = if (existing != null) {
                        dao.update(existing.copy(quantity = existing.quantity + r.quantity, updatedAt = now))
                        existing.id
                    } else {
                        dao.insert(row)
                    }
                    addedTo[rowId] = (addedTo[rowId] ?: 0) + r.quantity
                    val boxName = boxNames[target] ?: "?"
                    dao.addEvent(CardEvent(scryfallId = c.id, name = c.name, kind = "added", detail = "×${r.quantity} → $boxName"))
                    added += r.quantity
                }
                val points = plan.found.filterNotNull().distinctBy { it.id }.map { it.pricePoint() }
                if (points.isNotEmpty()) dao.putPricePoints(points)
            }
        }
        // One value snapshot for the whole import, not one per row.
        repo.appScope.launch {
            runCatching { repo.recordSnapshot() }
        }
        return ImportResult(added, notFound, addedTo.toList(), madeBox)
    }

}
