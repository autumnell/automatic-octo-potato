package com.mtgscanner.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.mtgscanner.MainActivity
import com.mtgscanner.R
import com.mtgscanner.widget.CollectionWidget
import java.time.LocalTime

/** Notification channels, quiet hours and the backup-failed note. */
object Notifications {
    private const val PREFS = "app"
    private const val KEY_QUIET = "quietNights"
    private const val QUIET_CHANNEL = "prices_quiet"
    private const val BACKUP_CHANNEL = "backup"
    private const val DIGEST_CHANNEL = "digest"
    private const val DIGEST_WORK = "weekly-digest"
    private const val BACKUP_ID = 1003

    /** No sound between 10 pm and 8 am (on unless turned off). */
    fun quietNights(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_QUIET, true)
    fun setQuietNights(context: Context, on: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_QUIET, on).apply()

    /** Pure rule: [hour] (0–23) is at night. */
    fun isNight(hour: Int): Boolean = hour >= 22 || hour < 8

    private fun quietNow(context: Context) = quietNights(context) && isNight(LocalTime.now().hour)

    private fun channel(context: Context, id: String, name: String, importance: Int) {
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(id, name, importance))
        }
    }

    /**
     * A builder on [normalChannel] (made with [normalName]), or at night (when quiet hours are on) on a silent
     * channel, so an alert never rings at 3 am.
     */
    fun builder(context: Context, normalChannel: String, normalName: String): NotificationCompat.Builder {
        val quiet = quietNow(context)
        val id = if (quiet) QUIET_CHANNEL else normalChannel
        if (quiet) channel(context, QUIET_CHANNEL, "At night (silent)", NotificationManager.IMPORTANCE_LOW)
        else channel(context, normalChannel, normalName, NotificationManager.IMPORTANCE_DEFAULT)
        return NotificationCompat.Builder(context, id).setSmallIcon(R.drawable.ic_stat_price).setSilent(quiet)
    }

    /** Opens the app at [where] ("home", "card/12", "settings"…), reusing the open screen. */
    fun openApp(context: Context, where: String, requestCode: Int): PendingIntent {
        val i = Intent(context, MainActivity::class.java)
            .putExtra(CollectionWidget.EXTRA_OPEN, where)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, requestCode, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** "Backup failed" (or [text]), tapping opens Settings. */
    fun backupFailed(context: Context, text: String = "Tap to try again") {
        if (!PriceAlerts.canNotify(context)) return
        val n = builder(context, BACKUP_CHANNEL, "Backups")
            .setContentTitle("Weekly backup failed")
            .setContentText(text)
            .setContentIntent(openApp(context, "settings", 9))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(BACKUP_ID, n) }
    }

    fun clearBackupFailed(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(BACKUP_ID) }
    }

    /** The weekly summary is gone: stops its job and removes its channel on phones that had it on. */
    fun dropDigest(context: Context) {
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(DIGEST_WORK) }
        if (Build.VERSION.SDK_INT >= 26) {
            runCatching { context.getSystemService(NotificationManager::class.java).deleteNotificationChannel(DIGEST_CHANNEL) }
        }
    }
}
