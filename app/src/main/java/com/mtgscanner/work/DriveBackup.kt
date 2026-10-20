package com.mtgscanner.work

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import java.util.concurrent.TimeUnit

/** Once a week: write a full backup to the file the user picked (for example on Google Drive). */
class DriveBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        DriveBackup.load(applicationContext)
        if (DriveBackup.uri == null) return Result.success()
        return if (DriveBackup.backupNow(applicationContext)) Result.success() else Result.retry()
    }
}

object DriveBackup {
    private const val WORK = "weekly-backup"
    private const val PREFS = "backup"
    private var loaded = false

    /** Where weekly backups go; null = off. */
    var uri by mutableStateOf<Uri?>(null)
        private set
    /** When the last weekly or manual backup to [uri] finished (0 = never). */
    var lastBackup by mutableLongStateOf(0L)
        private set

    fun load(context: Context) {
        if (loaded) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        uri = p.getString("uri", null)?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        lastBackup = p.getLong("last", 0L)
        loaded = true
        // A setting copied by Android's own backup to a new phone points at a file this install can't write.
        val target = uri
        if (target != null && context.contentResolver.persistedUriPermissions.none { it.uri == target && it.isWritePermission }) {
            uri = null
            p.edit().remove("uri").apply()
            WorkManager.getInstance(context).cancelUniqueWork(WORK)
        }
    }

    /** Remembers [target] (keeping access after restarts) and starts the weekly job. */
    fun enable(context: Context, target: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                target, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        uri = target
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("uri", target.toString()).apply()
        val req = PeriodicWorkRequestBuilder<DriveBackupWorker>(7, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
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

    /**
     * Overwrites the backup file now. Returns false when it couldn't be written.
     * The backup is built in full first; the old file is only replaced once there's a complete new one.
     */
    suspend fun backupNow(context: Context): Boolean {
        val target = uri ?: return false
        val app = context.applicationContext
        val repo = (app as MtgApp).repository
        val ok = try {
            BackupIO.export(repo) {
                // "wt" truncates the old backup; some providers only accept "w".
                runCatching { app.contentResolver.openOutputStream(target, "wt") }.getOrNull()
                    ?: app.contentResolver.openOutputStream(target, "w")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
        if (ok) {
            val now = System.currentTimeMillis()
            lastBackup = now
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("last", now).apply()
        }
        return ok
    }
}
