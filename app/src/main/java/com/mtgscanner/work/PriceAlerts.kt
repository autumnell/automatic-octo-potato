package com.mtgscanner.work

import android.Manifest
import android.content.Context
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
import com.mtgscanner.MtgApp
import com.mtgscanner.data.BackupIO
import com.mtgscanner.data.PriceMove
import com.mtgscanner.data.db.nearMintPrice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.mtgscanner.ui.theme.AppSettings
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Once a day: refresh prices, notify about big moves on cards you own or want, and check per-card price alerts. */
class PriceAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val wantMoves = AppSettings.alertsWanted(ctx)
        val repo = (ctx as MtgApp).repository
        // Alerts only count for cards still owned: one on a card that's gone can never fire.
        val owned = if (CardAlertStore.hasAny(ctx)) runCatching { repo.dao.all() }.getOrDefault(emptyList()) else emptyList()
        val ownedKeys = owned.map { CardPriceAlert.key(it.scryfallId, it.foil) }.toSet()
        val wantCards = CardAlertStore.load(ctx).any { it.key in ownedKeys }
        if (!wantMoves && !wantCards) {
            // Nothing left to watch: stop the daily job until an alert is set again.
            PriceAlerts.setIdle(ctx, true)
            PriceAlerts.schedule(ctx)
            return Result.success()
        }
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
            // A few tries with WorkManager's growing pause; after that, wait for tomorrow's run.
            return if (runAttemptCount < 3) Result.retry() else Result.success()
        }
        if (moves.isNotEmpty()) PriceAlerts.notify(ctx, moves)
        // Per-card alerts, against the prices just refreshed (near-mint market price for the finish).
        if (wantCards) {
            val prices = HashMap<String, Double?>()
            val ids = HashMap<String, Long>()
            runCatching { repo.dao.all() }.getOrDefault(owned).forEach { c ->
                val key = CardPriceAlert.key(c.scryfallId, c.foil)
                if (prices[key] == null) prices[key] = c.nearMintPrice()
                ids.putIfAbsent(key, c.id)
            }
            val hits = CardAlertStore.check(ctx, prices)
            if (hits.isNotEmpty()) PriceAlerts.notifyCardAlerts(ctx, hits, ids)
        }
        BackupIO.prunePriceHistory(repo.db)
        return Result.success()
    }
}

object PriceAlerts {
    private const val WORK = "price-alerts"
    private const val CHANNEL = "prices"

    private const val KEY_IDLE = "cardAlertsIdle"

    /** Every per-card alert is on a card no longer owned, so the daily job isn't needed for them. */
    fun setIdle(context: Context, idle: Boolean) =
        context.getSharedPreferences("app", Context.MODE_PRIVATE).edit().putBoolean(KEY_IDLE, idle).apply()

    private fun idle(context: Context) = context.getSharedPreferences("app", Context.MODE_PRIVATE).getBoolean(KEY_IDLE, false)

    /** Starts or stops the daily check to match the setting. */
    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        if (idle(context) && CardAlertStore.hasAny(context)) wakeIfOwned(context)
        if (!AppSettings.alertsWanted(context) && (!CardAlertStore.hasAny(context) || idle(context))) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val req = PeriodicWorkRequestBuilder<PriceAlertWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Turns the daily check back on once a card with an alert is owned again (say, sold and then added back). */
    private fun wakeIfOwned(context: Context) {
        val app = context.applicationContext as? MtgApp ?: return
        app.repository.appScope.launch {
            val owned = runCatching { app.repository.dao.all() }.getOrNull() ?: return@launch
            val keys = owned.mapTo(HashSet()) { CardPriceAlert.key(it.scryfallId, it.foil) }
            if (CardAlertStore.load(app).any { it.key in keys }) {
                setIdle(app, false)
                schedule(app)
            }
        }
    }

    /** Whether a notification would actually be shown: permission granted and notifications on for the app. */
    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * "Ragavan went over $50 · now $52.10", one notification for all crossings. Tapping opens the card
     * ([cardIds]: alert key to a row id), or Home's alert list when there are several.
     */
    fun notifyCardAlerts(context: Context, hits: List<AlertHit>, cardIds: Map<String, Long> = emptyMap()) {
        if (!canNotify(context) || hits.isEmpty()) return
        fun line(h: AlertHit): String {
            val at = if (h.up) h.alert.above else h.alert.below
            val foil = if (h.alert.foil) " (foil)" else ""
            return String.format(Locale.US, "%s%s %s $%.2f · now $%.2f", h.alert.name, foil, if (h.up) "over" else "under", at ?: 0.0, h.price)
        }
        val title = if (hits.size == 1) "Price alert: ${hits[0].alert.name}" else "${hits.size} price alerts"
        val style = NotificationCompat.InboxStyle()
        hits.take(6).forEach { style.addLine(line(it)) }
        if (hits.size > 6) style.setSummaryText("and ${hits.size - 6} more")
        val ids = hits.mapNotNull { cardIds[it.alert.key] }.distinct()
        val open = Notifications.openApp(context, if (ids.size == 1) "card/${ids[0]}" else "home", 8)
        val n = Notifications.builder(context, CHANNEL, "Price alerts")
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
        fun line(m: PriceMove): String {
            val pct = if (m.then > 0) (m.now - m.then) / m.then * 100 else 0.0
            val arrow = if (m.now >= m.then) "▲" else "▼"
            return String.format(Locale.US, "%s %s $%.2f → $%.2f (%+.0f%%)%s", arrow, m.name, m.then, m.now, pct, if (m.wanted) " · wishlist" else "")
        }
        val title = if (moves.size == 1) "${moves[0].name} moved in price" else "${moves.size} cards moved in price"
        val style = NotificationCompat.InboxStyle()
        moves.take(6).forEach { style.addLine(line(it)) }
        if (moves.size > 6) style.setSummaryText("and ${moves.size - 6} more")
        val open = Notifications.openApp(context, "home", 7)
        val n = Notifications.builder(context, CHANNEL, "Price alerts")
            .setContentTitle(title)
            .setContentText(line(moves[0]))
            .setStyle(style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(1001, n) }
    }
}
