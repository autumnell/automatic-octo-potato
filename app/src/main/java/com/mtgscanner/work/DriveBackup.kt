package com.mtgscanner.work

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mtgscanner.MtgApp
import com.mtgscanner.data.BackupIO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Once a week: write a full backup to the file the user picked (for example on Google Drive).
 * A failure is tried twice more, then shown as a notification instead of retrying quietly forever.
 */
class DriveBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        DriveBackup.load(ctx)
        if (DriveBackup.uri == null) return Result.success()
        return when (DriveBackup.backup(ctx, force = false)) {
            DriveBackup.Outcome.SAVED -> Result.success()
            DriveBackup.Outcome.SHRANK -> {
                Notifications.backupFailed(ctx, "Far fewer cards than last time. Back up in Settings to keep it")
                Result.success()
            }
            DriveBackup.Outcome.FAILED -> if (runAttemptCount < 2) Result.retry() else {
                Notifications.backupFailed(ctx)
                Result.success()
            }
        }
    }
}

object DriveBackup {
    private const val WORK = "weekly-backup"
    private const val PREFS = "backup"
    private var loaded = false

    /** Where weekly backups go; null = off. */
    var uri by mutableStateOf<Uri?>(null)
        private set
    /** How many dated backups a picked folder keeps. */
    var keep by mutableStateOf(KEEP_IN_FOLDER)
        private set

    /** Whether weekly backups go to a folder (dated copies) rather than over one file. */
    val toFolder: Boolean get() = uri?.let { DocumentsContract.isTreeUri(it) } == true

    /** When the last weekly or manual backup to [uri] finished (0 = never). */
    var lastBackup by mutableLongStateOf(0L)
        private set

    /** A new install found a weekly backup set up on the old phone: offer to restore it. */
    var offerRestore by mutableStateOf(false)
        private set

    fun load(context: Context) {
        if (loaded) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        uri = p.getString("uri", null)?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        lastBackup = p.getLong("last", 0L)
        keep = p.getInt("keep", KEEP_IN_FOLDER).coerceAtLeast(1)
        offerRestore = p.getBoolean("offerRestore", false)
        loaded = true
        // A setting copied by Android's own backup to a new phone points at a file this install can't write.
        val target = uri
        if (target != null && context.contentResolver.persistedUriPermissions.none { it.uri == target && it.isWritePermission }) {
            uri = null
            offerRestore = true
            p.edit().remove("uri").putBoolean("offerRestore", true).apply()
            WorkManager.getInstance(context).cancelUniqueWork(WORK)
        }
    }

    /** Stops offering to restore (restored, declined, or the app already has cards). */
    fun dismissRestoreOffer(context: Context) {
        offerRestore = false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("offerRestore", false).apply()
    }

    /**
     * Remembers [target] (keeping access after restarts) and starts the weekly job. False when the place picked
     * can't keep access after a restart: weekly backups there would stop by themselves, so it isn't turned on.
     */
    fun enable(context: Context, target: Uri): Boolean {
        val kept = runCatching {
            context.contentResolver.takePersistableUriPermission(
                target, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.isSuccess
        if (!kept) return false
        uri = target
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("uri", target.toString()).apply()
        val req = PeriodicWorkRequestBuilder<DriveBackupWorker>(7, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
        return true
    }

    fun disable(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
        uri?.let { old ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    old, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        uri = null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("uri").apply()
    }

    enum class Outcome { SAVED, FAILED, SHRANK }

    /** How many dated backups a folder keeps unless changed. */
    const val KEEP_IN_FOLDER = 8
    private const val PREFIX = "grimoire-backup-"

    fun setKeep(context: Context, n: Int) {
        keep = n.coerceAtLeast(1)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("keep", keep).apply()
    }

    /** One dated backup in the picked folder. */
    data class FolderBackup(val name: String, val uri: Uri, val modified: Long, val size: Long)

    /** The dated backups in the picked folder, newest first. Empty when weekly backups go to one file. */
    suspend fun folderBackups(context: Context): List<FolderBackup> = withContext(Dispatchers.IO) {
        val tree = uri?.takeIf { DocumentsContract.isTreeUri(it) } ?: return@withContext emptyList()
        runCatching { listFolder(context, tree) }.getOrDefault(emptyList())
            .filter { it.name.startsWith(PREFIX) && it.name.endsWith(".zip") }
            .sortedWith(compareByDescending<FolderBackup> { it.name }.thenByDescending { it.modified })
    }

    private fun listFolder(context: Context, tree: Uri): List<FolderBackup> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED, DocumentsContract.Document.COLUMN_SIZE,
        )
        val out = ArrayList<FolderBackup>()
        context.contentResolver.query(children, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                out += FolderBackup(
                    name = c.getString(1).orEmpty(), uri = DocumentsContract.buildDocumentUriUsingTree(tree, id),
                    modified = if (c.isNull(2)) 0 else c.getLong(2), size = if (c.isNull(3)) 0 else c.getLong(3),
                )
            }
        }
        return out
    }

    /** Writes [file] into the folder as [name] (replacing a copy from the same day), then keeps the newest [keep]. */
    private fun writeToFolder(context: Context, tree: Uri, name: String, file: File) {
        val cr = context.contentResolver
        val existing = listFolder(context, tree)
        existing.filter { it.name == name }.forEach { runCatching { DocumentsContract.deleteDocument(cr, it.uri) } }
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val doc = DocumentsContract.createDocument(cr, parent, "application/zip", name) ?: throw IOException("Couldn't make the backup file")
        (cr.openOutputStream(doc, "w") ?: throw IOException("Couldn't open the backup file")).use { o -> file.inputStream().use { it.copyTo(o) } }
        listFolder(context, tree)
            .filter { it.name.startsWith(PREFIX) && it.name.endsWith(".zip") }
            .sortedByDescending { it.name }
            .drop(keep)
            .forEach { runCatching { DocumentsContract.deleteDocument(cr, it.uri) } }
    }

    /** How many dated copies are kept on the phone (filesDir/backups). */
    private const val KEEP = 4

    /** Overwrites the backup file now (a manual backup, so a much smaller collection is saved too). Returns false when it couldn't be written. */
    suspend fun backupNow(context: Context): Boolean = backup(context, force = true) == Outcome.SAVED

    /**
     * Writes a full backup: first a dated copy on the phone (the last [KEEP] are kept), then into the picked folder as a
     * dated file (the last [keep] are kept) or over the picked file.
     * Unless [force]d, a collection that shrank to under a quarter of the last backup isn't written over it
     * ([Outcome.SHRANK]), so a wiped collection never replaces a good backup by itself.
     */
    suspend fun backup(context: Context, force: Boolean): Outcome {
        val target = uri ?: return Outcome.FAILED
        val app = context.applicationContext
        val repo = (app as MtgApp).repository
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val dir = File(app.filesDir, "backups").apply { mkdirs() }
        val dated = File(dir, "grimoire-backup-${LocalDate.now()}.zip")
        return try {
            // The shrink check comes first: a wiped collection writes no dated copy and prunes none of the good ones.
            val last = p.getInt("lastCards", -1)
            var shrank = false
            val count = BackupIO.exportCounted(repo) { n ->
                if (!force && last >= 20 && n < last / 4) { shrank = true; null } else dated.outputStream()
            } ?: return if (shrank) Outcome.SHRANK else Outcome.FAILED
            dir.listFiles { f -> f.name.startsWith("grimoire-backup-") }?.sortedByDescending { it.name }?.drop(KEEP)?.forEach { it.delete() }
            if (DocumentsContract.isTreeUri(target)) withContext(Dispatchers.IO + NonCancellable) {
                writeToFolder(app, target, dated.name, dated)
            } else withContext(Dispatchers.IO + NonCancellable) {
                // "wt" truncates the old backup; some providers only accept "w".
                val out = runCatching { app.contentResolver.openOutputStream(target, "wt") }.getOrNull()
                    ?: app.contentResolver.openOutputStream(target, "w")
                    ?: throw IOException("Couldn't open the backup file")
                out.use { o -> dated.inputStream().use { it.copyTo(o) } }
            }
            val now = System.currentTimeMillis()
            lastBackup = now
            p.edit().putLong("last", now).putInt("lastCards", count).apply()
            Notifications.clearBackupFailed(app)
            Outcome.SAVED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Outcome.FAILED
        }
    }
}
