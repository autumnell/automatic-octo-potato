package com.mtgscanner.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mtgscanner.MainActivity
import com.mtgscanner.MtgApp
import com.mtgscanner.R
import com.mtgscanner.widget.CollectionWidget
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Notification channels, quiet hours, the backup-failed note and the optional weekly summary. */
object Notifications {
    private const val PREFS = "app"
    private const val KEY_QUIET = "quietNights"
    private const val KEY_DIGEST = "weeklyDigest"
    private const val QUIET_CHANNEL = "prices_quiet"
    private const val BACKUP_CHANNEL = "backup"
    private const val DIGEST_CHANNEL = "digest"
    private const val DIGEST_WORK = "weekly-digest"
    private const val BACKUP_ID = 1003
    private const val DIGEST_ID = 1004

    /** No sound between 10 pm and 8 am (on unless turned off). */
    fun quietNights(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_QUIET, true)
    fun setQuietNights(context: Context, on: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_QUIET, on).apply()

    fun weeklyDigest(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DIGEST, false)
    fun setWeeklyDigest(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DIGEST, on).apply()
        scheduleDigest(context)
    }

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

    /** Starts or stops the weekly summary to match the setting. */
    fun scheduleDigest(context: Context) {
        val wm = WorkManager.getInstance(context)
        if (!weeklyDigest(context)) {
            wm.cancelUniqueWork(DIGEST_WORK)
            return
        }
        wm.enqueueUniquePeriodicWork(
            DIGEST_WORK, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DigestWorker>(7, TimeUnit.DAYS).build(),
        )
    }

    /** "$1,234 · up $56 this week" / "812 cards · 23 new this week". */
    fun digestText(value: Double, valueWeekAgo: Double?, cards: Int, cardsWeekAgo: Int?, showPrices: Boolean): String {
        val money = NumberFormat.getCurrencyInstance(Locale.US)
        val newCards = cardsWeekAgo?.let { cards - it }?.takeIf { it > 0 }
        val parts = mutableListOf<String>()
        if (showPrices) {
            parts += money.format(value)
            valueWeekAgo?.let { old ->
                val d = value - old
                if (kotlin.math.abs(d) >= 0.01) parts += (if (d > 0) "up " else "down ") + money.format(kotlin.math.abs(d)) + " this week"
            }
        }
        parts += "$cards cards"
        newCards?.let { parts += "$it new this week" }
        return parts.joinToString(" · ")
    }

    internal fun notifyDigest(context: Context, text: String) {
        if (!PriceAlerts.canNotify(context)) return
        val n = builder(context, DIGEST_CHANNEL, "Weekly summary")
            .setContentTitle("Your week in cards")
            .setContentText(text)
            .setContentIntent(openApp(context, "home", 10))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(DIGEST_ID, n) }
    }
}

/** Once a week: collection value and new cards since last week. Uses saved values only, no downloads. */
class DigestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!Notifications.weeklyDigest(ctx)) return Result.success()
        val repo = (ctx as MtgApp).repository
        runCatching { repo.recordSnapshot() }
        val snaps = runCatching { repo.dao.allSnapshots() }.getOrDefault(emptyList()).sortedBy { it.day }
        val now = snaps.lastOrNull() ?: return Result.success()
        if (now.cards == 0) return Result.success()
        val weekAgo = LocalDate.now().minusDays(7).toString()
        val then = snaps.lastOrNull { it.day <= weekAgo }
        val showPrices = ctx.getSharedPreferences("app", Context.MODE_PRIVATE).getBoolean("showPrices", true)
        Notifications.notifyDigest(ctx, Notifications.digestText(now.value, then?.value, now.cards, then?.cards, showPrices))
        return Result.success()
    }
}
