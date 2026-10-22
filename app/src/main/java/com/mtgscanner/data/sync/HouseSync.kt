package com.mtgscanner.data.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.room.withTransaction
import com.mtgscanner.MtgApp
import com.mtgscanner.data.db.Box
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.mergedWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Shared collection (beta): this phone and another one keep the same cards through one file both can
 * reach, like a file in a shared Google Drive folder. See [SyncMerge] for how changes are merged.
 *
 * Everything this phone keeps for it lives in filesDir/house-sync, which Android's backup leaves out,
 * so a new phone set up from a backup doesn't take over the old phone's device id.
 */
object HouseSync {
    private const val DIR = "house-sync"
    /** Deletes are kept this long (and until both phones have synced), then forgotten. */
    private const val KEEP_DELETES_MS = 60L * 24 * 60 * 60 * 1000
    /** Opening the app syncs at most this often. */
    private const val OPEN_SYNC_GAP_MS = 5L * 60 * 1000

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Mutex()
    private var loaded = false

    @Serializable
    private data class Prefs(val uri: String = "", val device: String = "", val lastSync: Long = 0, val otherSync: Long = 0)

    /** The shared file; null = off. */
    var uri by mutableStateOf<Uri?>(null)
        private set
    /** When this phone last synced (0 = never). */
    var lastSync by mutableLongStateOf(0L)
        private set
    /** When the other phone last synced, as of our last sync (0 = not yet). */
    var otherSync by mutableLongStateOf(0L)
        private set
    var syncing by mutableStateOf(false)
        private set
    private var device = ""

    enum class Join { COMBINE, USE_SHARED }

    sealed interface Outcome {
        data class Done(val changes: Int) : Outcome
        data class Failed(val why: String) : Outcome
    }

    private fun dir(ctx: Context) = File(ctx.applicationContext.filesDir, DIR).apply { mkdirs() }

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = runCatching { json.decodeFromString(Prefs.serializer(), File(dir(context), "prefs.json").readText()) }.getOrDefault(Prefs())
        device = p.device.ifBlank { UUID.randomUUID().toString().take(8) }
        lastSync = p.lastSync
        otherSync = p.otherSync
        val target = p.uri.takeIf { it.isNotBlank() }?.let(Uri::parse)
        // A file this install can no longer open (say, the app was reinstalled) turns sync off.
        uri = target?.takeIf { t -> context.contentResolver.persistedUriPermissions.any { it.uri == t && it.isWritePermission } }
        if (target != null && uri == null) savePrefs(context)
        if (p.device.isBlank()) savePrefs(context)
    }

    private fun savePrefs(context: Context) {
        val p = Prefs(uri?.toString() ?: "", device, lastSync, otherSync)
        runCatching { File(dir(context), "prefs.json").writeText(json.encodeToString(Prefs.serializer(), p)) }
    }

    /** Uses [target] from now on, keeping access to it after restarts. Starts as never synced. */
    fun link(context: Context, target: Uri) {
        load(context)
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                target, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        uri = target
        lastSync = 0
        otherSync = 0
        File(dir(context), "state.json.gz").delete()
        File(dir(context), "ids.json").delete()
        savePrefs(context)
    }

    fun unlink(context: Context) {
        load(context)
        uri?.let { old ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    old, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        uri = null
        lastSync = 0
        otherSync = 0
        File(dir(context), "state.json.gz").delete()
        File(dir(context), "ids.json").delete()
        savePrefs(context)
    }

    /** Cards in the shared file, or 0 when it's new or empty. */
    suspend fun cardsIn(context: Context, target: Uri): Int = withContext(Dispatchers.IO) {
        runCatching { readDoc(context, target)?.aliveCount ?: 0 }.getOrDefault(0)
    }

    /** Syncs when the app opens, if it's on and hasn't synced in the last few minutes. */
    fun syncOnOpen(context: Context) {
        val app = context.applicationContext as? MtgApp ?: return
        load(app)
        if (uri == null || syncing || System.currentTimeMillis() - lastSync < OPEN_SYNC_GAP_MS) return
        app.repository.appScope.launch { runCatching { sync(app) } }
    }

    /**
     * Folds this phone's changes into the shared file and applies the other phone's here.
     * [join] is for the first sync with a file that already has cards: combine both, or replace this phone's.
     */
    suspend fun sync(context: Context, join: Join = Join.COMBINE): Outcome = lock.withLock {
        val app = context.applicationContext as MtgApp
        load(app)
        val target = uri ?: return Outcome.Failed("Not set up")
        syncing = true
        try {
            withContext(Dispatchers.IO) { syncLocked(app, target, join) }
        } catch (e: Exception) {
            Outcome.Failed(e.message?.take(80) ?: "Couldn't sync")
        } finally {
            syncing = false
        }
    }

    private suspend fun syncLocked(app: MtgApp, target: Uri, join: Join): Outcome {
        val repo = app.repository
        val dao = repo.dao
        val remote = try {
            readDoc(app, target) ?: SyncDoc()
        } catch (e: Exception) {
            return Outcome.Failed("Can't read the shared file")
        }
        val d = dir(app)
        var state = if (lastSync == 0L) SyncDoc() else readState(d)
        var ids = if (lastSync == 0L) IdMap() else readIds(d)

        if (join == Join.USE_SHARED && lastSync == 0L) {
            repo.db.withTransaction { dao.all().forEach { dao.delete(it) } }
            state = SyncDoc()
            ids = IdMap()
        }

        val boxes = dao.allBoxes().toMutableList()
        val cards = dao.all()
        ids = ids.assign(cards.map { it.id to it.scryfallId }) { UUID.randomUUID().toString() }
        val local = cards.map { c ->
            LocalRow(
                id = ids.idOf(c.id, c.scryfallId)!!,
                fields = SyncFields.of(c, boxes.firstOrNull { it.id == c.boxId }?.name ?: ""),
                base = SyncFields.base(c),
                updatedAt = c.updatedAt,
            )
        }
        val now = System.currentTimeMillis()
        val folded = SyncMerge.foldLocal(state, local, device, now, lastSync)
        val merged = SyncMerge.prune(SyncMerge.merge(folded, remote), now - KEEP_DELETES_MS)

        if (!writeDoc(app, target, merged)) return Outcome.Failed("Can't write the shared file")

        val plan = SyncMerge.plan(merged, local)
        if (!plan.isEmpty) ids = apply(app, plan, ids, boxes, now)

        writeState(d, merged)
        writeIds(d, ids)
        lastSync = now
        otherSync = merged.devices.filterKeys { it != device }.values.maxOrNull() ?: 0L
        savePrefs(app)
        if (!plan.isEmpty) runCatching { repo.recordSnapshot() }
        return Outcome.Done(plan.size)
    }

    /** Makes this phone's rows match [plan]. Returns the updated id map. */
    private suspend fun apply(app: MtgApp, plan: ApplyPlan, start: IdMap, boxes: MutableList<Box>, now: Long): IdMap {
        val repo = app.repository
        val dao = repo.dao
        var ids = start
        // Shared id → row id, for the rows that were here before this sync.
        val rowFor = start.rows.entries.associate { it.value.id to it.key }

        suspend fun boxFor(name: String, fallback: Long): Long {
            if (name.isBlank()) return fallback
            boxes.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { return it.id }
            val id = dao.insertBox(Box(name = name, sortOrder = (boxes.maxOfOrNull { it.sortOrder } ?: 0) + 1))
            boxes += Box(id = id, name = name)
            return id
        }

        /** Saves [c] (new when id is 0), or folds it into an identical row already there. Returns the row id, or null when folded. */
        suspend fun save(c: CollectionCard, oldRowId: Long?): Long? {
            val twin = repo.twinOf(c)?.takeIf { it.id != c.id }
            return if (twin != null) {
                dao.update(twin.mergedWith(c, now = now))
                if (oldRowId != null) dao.get(oldRowId)?.let { dao.delete(it) }
                null
            } else if (c.id == 0L) {
                dao.insert(c)
            } else {
                dao.update(c)
                c.id
            }
        }

        repo.db.withTransaction {
            for (id in plan.deletes) {
                val rowId = rowFor[id] ?: continue
                dao.get(rowId)?.let { dao.delete(it) }
                ids = ids.without(rowId)
            }
            for ((id, f) in plan.updates) {
                val rowId = rowFor[id] ?: continue
                val row = dao.get(rowId) ?: continue
                val changed = SyncFields.apply(row, f).copy(boxId = f[SyncFields.BOX]?.let { boxFor(it, row.boxId) } ?: row.boxId, updatedAt = now)
                if (save(changed, rowId) == null) ids = ids.without(rowId)
            }
            val fallback = boxes.firstOrNull { it.fallback }?.id ?: boxes.firstOrNull()?.id
            for (r in plan.inserts) {
                val base = SyncFields.fromBase(r.base) ?: continue
                val f = r.values()
                val boxId = boxFor(f[SyncFields.BOX] ?: "", fallback ?: boxFor("Shared", 0L))
                val card = SyncFields.apply(base, f).copy(id = 0, boxId = boxId, updatedAt = now)
                val rowId = save(card, null) ?: continue
                ids = ids.with(rowId, IdEntry(r.id, card.scryfallId))
            }
        }
        return ids
    }

    // Files

    private fun readDoc(context: Context, target: Uri): SyncDoc? {
        val bytes = context.contentResolver.openInputStream(target)?.use { it.readBytes() } ?: return null
        if (bytes.isEmpty()) return null
        val text = if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
            GZIPInputStream(bytes.inputStream()).bufferedReader().use { it.readText() }
        } else {
            bytes.decodeToString()
        }
        if (text.isBlank()) return null
        return json.decodeFromString(SyncDoc.serializer(), text)
    }

    private fun writeDoc(context: Context, target: Uri, doc: SyncDoc): Boolean = runCatching {
        val out = runCatching { context.contentResolver.openOutputStream(target, "wt") }.getOrNull()
            ?: context.contentResolver.openOutputStream(target)
            ?: return false
        GZIPOutputStream(out).bufferedWriter().use { it.write(json.encodeToString(SyncDoc.serializer(), doc)) }
        true
    }.getOrDefault(false)

    private fun readState(d: File): SyncDoc = runCatching {
        GZIPInputStream(File(d, "state.json.gz").inputStream()).bufferedReader().use { json.decodeFromString(SyncDoc.serializer(), it.readText()) }
    }.getOrDefault(SyncDoc())

    private fun writeState(d: File, doc: SyncDoc) {
        val tmp = File(d, "state.json.gz.tmp")
        GZIPOutputStream(tmp.outputStream()).bufferedWriter().use { it.write(json.encodeToString(SyncDoc.serializer(), doc)) }
        tmp.renameTo(File(d, "state.json.gz"))
    }

    private fun readIds(d: File): IdMap = runCatching {
        json.decodeFromString(IdMap.serializer(), File(d, "ids.json").readText())
    }.getOrDefault(IdMap())

    private fun writeIds(d: File, ids: IdMap) {
        val tmp = File(d, "ids.json.tmp")
        tmp.writeText(json.encodeToString(IdMap.serializer(), ids))
        tmp.renameTo(File(d, "ids.json"))
    }
}
