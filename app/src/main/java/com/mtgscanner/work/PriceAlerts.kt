package com.mtgscanner.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mtgscanner.MainActivity
import com.mtgscanner.MtgApp
import com.mtgscanner.R
import com.mtgscanner.data.BackupIO
import com.mtgscanner.data.PriceMove
import kotlinx.coroutines.CancellationException
import com.mtgscanner.ui.theme.AppSettings
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Once a day: refresh prices and notify about big moves on cards you own or want. */
class PriceAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!AppSettings.alertsWanted(applicationContext)) return Result.success()
        val repo = (applicationContext as MtgApp).repository
        // Without permission to notify, don't check: checking moves each card's "last alerted" price,
        // so moves found now would never be shown.
        if (!PriceAlerts.canNotify(applicationContext)) {
            runCatching { repo.refreshPricesIfStale() }
            BackupIO.prunePriceHistory(repo.db)
            return Result.success()
        }
        val p = applicationContext.getSharedPreferences("app", Context.MODE_PRIVATE)
        val moves = try {
            repo.priceMoves(p.getFloat("alertPercent", 20f).toDouble(), p.getFloat("alertDollars", 1f).toDouble())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.retry()
        }
        if (moves.isNotEmpty()) PriceAlerts.notify(applicationContext, moves)
        BackupIO.prunePriceHistory(repo.db)
        return Result.success()
    }
}

object PriceAlerts {
    private const val WORK = "price-alerts"
    private const val CHANNEL = "prices"

    /** Starts or stops the daily check to match the setting. */
    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        if (!AppSettings.alertsWanted(context)) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val req = PeriodicWorkRequestBuilder<PriceAlertWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Whether a notification would actually be shown: permission granted and notifications on for the app. */
    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun notify(context: Context, moves: List<PriceMove>) {
        if (!canNotify(context)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Price alerts", NotificationManager.IMPORTANCE_DEFAULT))
        }
        fun line(m: PriceMove): String {
            val pct = if (m.then > 0) (m.now - m.then) / m.then * 100 else 0.0
            val arrow = if (m.now >= m.then) "▲" else "▼"
            return String.format(Locale.US, "%s %s $%.2f → $%.2f (%+.0f%%)%s", arrow, m.name, m.then, m.now, pct, if (m.wanted) " · wishlist" else "")
        }
        val title = if (moves.size == 1) "${moves[0].name} moved in price" else "${moves.size} cards moved in price"
        val style = NotificationCompat.InboxStyle()
        moves.take(6).forEach { style.addLine(line(it)) }
        if (moves.size > 6) style.setSummaryText("and ${moves.size - 6} more")
        val open = PendingIntent.getActivity(
            context, 7, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_price)
            .setContentTitle(title)
            .setContentText(line(moves[0]))
            .setStyle(style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(1001, n) }
    }
}
