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
import com.mtgscanner.data.db.nearMintPrice
import kotlinx.coroutines.CancellationException
import com.mtgscanner.ui.theme.AppSettings
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Once a day: refresh prices, notify about big moves on cards you own or want, and check per-card price alerts. */
class PriceAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val wantMoves = AppSettings.alertsWanted(ctx)
        val wantCards = CardAlertStore.hasAny(ctx)
        if (!wantMoves && !wantCards) return Result.success()
        val repo = (ctx as MtgApp).repository
        // Without permission to notify, don't check: checking moves each card's "last alerted" price,
        // so moves found now would never be shown.
        if (!PriceAlerts.canNotify(ctx)) {
            runCatching { repo.refreshPricesIfStale() }
            BackupIO.prunePriceHistory(repo.db)
            return Result.success()
        }
        val p = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)
        val moves = try {
            if (wantMoves) repo.priceMoves(p.getFloat("alertPercent", 20f).toDouble(), p.getFloat("alertDollars", 1f).toDouble())
            else { repo.refreshPrices(maxAgeMs = 60L * 60 * 1000); emptyList<PriceMove>() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.retry()
        }
        if (moves.isNotEmpty()) PriceAlerts.notify(ctx, moves)
        // Per-card alerts, against the prices just refreshed (near-mint market price for the finish).
        if (wantCards) {
            val prices = HashMap<String, Double?>()
            runCatching { repo.dao.all() }.getOrDefault(emptyList()).forEach { c ->
                val key = CardPriceAlert.key(c.scryfallId, c.foil)
                if (prices[key] == null) prices[key] = c.nearMintPrice()
            }
            val hits = CardAlertStore.check(ctx, prices)
            if (hits.isNotEmpty()) PriceAlerts.notifyCardAlerts(ctx, hits)
        }
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
        if (!AppSettings.alertsWanted(context) && !CardAlertStore.hasAny(context)) {
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

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL, "Price alerts", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    /** "Ragavan went over $50 · now $52.10", one notification for all crossings. */
    fun notifyCardAlerts(context: Context, hits: List<AlertHit>) {
        if (!canNotify(context) || hits.isEmpty()) return
        ensureChannel(context)
        fun line(h: AlertHit): String {
            val at = if (h.up) h.alert.above else h.alert.below
            val foil = if (h.alert.foil) " (foil)" else ""
            return String.format(Locale.US, "%s%s %s $%.2f · now $%.2f", h.alert.name, foil, if (h.up) "over" else "under", at ?: 0.0, h.price)
        }
        val title = if (hits.size == 1) "Price alert: ${hits[0].alert.name}" else "${hits.size} price alerts"
        val style = NotificationCompat.InboxStyle()
        hits.take(6).forEach { style.addLine(line(it)) }
        if (hits.size > 6) style.setSummaryText("and ${hits.size - 6} more")
        val open = PendingIntent.getActivity(
            context, 8, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_price)
            .setContentTitle(title)
            .setContentText(line(hits[0]))
            .setStyle(style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(1002, n) }
    }

    fun notify(context: Context, moves: List<PriceMove>) {
        if (!canNotify(context)) return
        ensureChannel(context)
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
